import { mutation, query } from "./_generated/server";
import type { QueryCtx } from "./_generated/server";
import type { Doc } from "./_generated/dataModel";
import { v } from "convex/values";

import { requireActor, requireRegisteredDevice } from "./lib/auth";
import { fail } from "./lib/errors";
import { canonicalize, clampLimit, hasValidReceiptProof } from "./lib/state";
import { appendTrace } from "./lib/tracing";
import {
  actorInputValidator,
  traceAttributesValidator,
  traceCorrelationValidator,
  traceModeValidator,
  traceNameValidator,
  traceOutcomeValidator,
  traceServiceValidator,
} from "./lib/validators";

const DEVICE_SERVICES = new Set(["ANDROID_MOBILE", "TEXTURE_ENGINE"]);
const BRIDGE_SERVICES = new Set(["TEXTUREFLOW_BRIDGE"]);
const DEVICE_NAMES = new Set([
  "LISTENER_CONNECTED",
  "ACTIVE_SNAPSHOT_STARTED",
  "ACTIVE_SNAPSHOT_RECONCILED",
  "LISTENER_DISCONNECTED",
  "EVENT_RECEIVED",
  "EVENT_UPDATED",
  "EVENT_REMOVED",
  "EVENT_STORED_LOCAL",
  "OUTBOX_ENQUEUED",
  "EVENT_SYNCED",
  "ACTION_HANDLE_REGISTERED",
  "ACTION_HANDLE_REMOVED",
  "POLICY_VALIDATED",
  "ACTION_EXECUTION_STARTED",
  "ACTION_DISPATCHED",
  "ACTION_FAILED",
  "RECEIPT_STORED_LOCAL",
  "RECEIPT_SYNCED",
  "CUE_SCHEDULED",
  "CUE_RENDERED",
  "CUE_SUPPRESSED_FOR_SPEECH",
  "DEVICE_HEARTBEAT",
]);
const BRIDGE_NAMES = new Set([
  "VOICE_TOOL_CALLED",
  "ATTENTION_LISTED",
  "TRACE_LINK_MISSING",
]);

export const append = mutation({
  args: {
    actor: actorInputValidator,
    event: v.object({
      contractVersion: v.literal(1),
      mode: traceModeValidator,
      traceId: v.string(),
      spanId: v.string(),
      parentSpanId: v.optional(v.string()),
      sequence: v.optional(v.number()),
      name: traceNameValidator,
      service: traceServiceValidator,
      outcome: traceOutcomeValidator,
      occurredAt: v.string(),
      durationMs: v.optional(v.number()),
      correlation: traceCorrelationValidator,
      attributes: traceAttributesValidator,
    }),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["BRIDGE", "DEVICE"]);
    if (!args.event.traceId.trim() || !args.event.spanId.trim()) {
      fail("INVALID_TRACE", "Trace and span IDs are required.");
    }
    if (actor.role === "DEVICE") {
      const device = await requireRegisteredDevice(ctx, actor);
      if (!DEVICE_SERVICES.has(args.event.service) || !DEVICE_NAMES.has(args.event.name)) {
        fail("UNAUTHORIZED_TRACE_EVENT", "The device cannot append this trace span.");
      }
      const expectedMode = device.status === "REHEARSAL" ? "REHEARSAL" : "LIVE";
      if (args.event.mode !== expectedMode) {
        fail("TRACE_MODE_MISMATCH", "Trace mode does not match the registered device mode.");
      }
      if (
        expectedMode === "REHEARSAL" &&
        (args.event.name === "ACTION_DISPATCHED" ||
          args.event.name === "RECEIPT_STORED_LOCAL" ||
          args.event.name === "RECEIPT_SYNCED")
      ) {
        fail("REHEARSAL_PROOF_FORBIDDEN", "Rehearsal traces cannot claim device execution proof.");
      }
      if (
        args.event.correlation.deviceId &&
        args.event.correlation.deviceId !== device.deviceId
      ) {
        fail("UNAUTHORIZED", "Trace correlation references another device.");
      }
    } else if (
      !BRIDGE_SERVICES.has(args.event.service) ||
      !BRIDGE_NAMES.has(args.event.name)
    ) {
      fail("UNAUTHORIZED_TRACE_EVENT", "The bridge cannot append this trace span.");
    }

    const occurredAtMs = Date.parse(args.event.occurredAt);
    if (!Number.isFinite(occurredAtMs)) {
      fail("INVALID_TIMESTAMP", "Trace occurredAt must be ISO-8601.");
    }
    if (
      args.event.durationMs !== undefined &&
      (!Number.isFinite(args.event.durationMs) || args.event.durationMs < 0)
    ) {
      fail("INVALID_DURATION", "Trace duration must be a non-negative number.");
    }
    if (
      args.event.sequence !== undefined &&
      (!Number.isInteger(args.event.sequence) || args.event.sequence < 0)
    ) {
      fail("INVALID_SEQUENCE", "Trace sequence must be a non-negative integer.");
    }
    if (args.event.parentSpanId === args.event.spanId) {
      fail("INVALID_TRACE_PARENT", "A trace span cannot be its own parent.");
    }
    const existing = await ctx.db
      .query("traceEvents")
      .withIndex("by_owner_trace", (query) =>
        query.eq("ownerId", actor.ownerId).eq("traceId", args.event.traceId),
      )
      .filter((query) => query.eq(query.field("spanId"), args.event.spanId))
      .unique();
    if (existing) {
      const identical = canonicalize({
        contractVersion: existing.contractVersion,
        mode: existing.mode,
        traceId: existing.traceId,
        spanId: existing.spanId,
        parentSpanId: existing.parentSpanId,
        sequence: existing.sequence,
        name: existing.name,
        service: existing.service,
        outcome: existing.outcome,
        occurredAt: existing.occurredAt,
        durationMs: existing.durationMs,
        correlation: existing.correlation,
        attributes: existing.attributes,
      }) === canonicalize(args.event);
      if (!identical) {
        fail("TRACE_SPAN_CONFLICT", "The span ID already contains different trace data.");
      }
      return { operation: "IDEMPOTENT" as const, event: existing };
    }
    const id = await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: args.event.mode,
      traceId: args.event.traceId,
      spanId: args.event.spanId,
      parentSpanId: args.event.parentSpanId,
      sequence: args.event.sequence,
      name: args.event.name,
      service: args.event.service,
      outcome: args.event.outcome,
      durationMs: args.event.durationMs,
      correlation: args.event.correlation,
      attributes: args.event.attributes,
      occurredAtMs,
    });
    return { operation: "INSERT" as const, event: await ctx.db.get(id) };
  },
});

async function receiptProof(
  ctx: QueryCtx,
  ownerId: string,
  commandId: string | undefined,
) {
  if (!commandId) {
    return { proven: false as const, reason: "MISSING_COMMAND" };
  }
  const command = await ctx.db
    .query("commands")
    .withIndex("by_owner_command", (query) =>
      query.eq("ownerId", ownerId).eq("commandId", commandId),
    )
    .unique();
  if (!command) {
    return { proven: false as const, reason: "COMMAND_NOT_FOUND" };
  }
  const receipt = await ctx.db
    .query("actionReceipts")
    .withIndex("by_owner_command", (query) =>
      query.eq("ownerId", ownerId).eq("commandId", commandId),
    )
    .unique();
  const valid = hasValidReceiptProof(command, receipt);
  return valid
    ? {
        proven: true as const,
        status: receipt!.status,
        receiptId: receipt!.receiptId,
        deviceId: receipt!.deviceId,
      }
    : { proven: false as const, reason: "RECEIPT_PROOF_MISMATCH" };
}

const RECEIPT_EVIDENCE_NAMES = new Set([
  "ACTION_DISPATCHED",
  "ACTION_FAILED",
  "RECEIPT_STORED_LOCAL",
  "RECEIPT_SYNCED",
]);

async function projectEvidence(
  ctx: QueryCtx,
  ownerId: string,
  event: Doc<"traceEvents">,
) {
  const proof =
    event.mode === "LIVE" && RECEIPT_EVIDENCE_NAMES.has(event.name)
      ? await receiptProof(ctx, ownerId, event.correlation.commandId)
      : {
          proven: false as const,
          reason: event.mode === "REHEARSAL" ? "REHEARSAL_MODE" : "NOT_RECEIPT_EVIDENCE",
        };
  const evidenceClass =
    event.mode === "REHEARSAL"
      ? "REHEARSAL"
      : proof.proven
        ? "DEVICE_EVIDENCE"
        : "LIVE_COORDINATION";
  return { ...event, evidenceClass, receiptProof: proof };
}

export const list = query({
  args: {
    actor: actorInputValidator,
    traceId: v.string(),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const limit = clampLimit(args.limit, 100, 250);
    const events = await ctx.db
      .query("traceEvents")
      .withIndex("by_owner_trace", (query) =>
        query.eq("ownerId", actor.ownerId).eq("traceId", args.traceId),
      )
      .order("asc")
      .take(limit);
    return await Promise.all(events.map((event) => projectEvidence(ctx, actor.ownerId, event)));
  },
});

export const recent = query({
  args: { actor: actorInputValidator, limit: v.optional(v.number()) },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const limit = clampLimit(args.limit, 100, 250);
    const events = await ctx.db
      .query("traceEvents")
      .withIndex("by_owner_occurred", (query) => query.eq("ownerId", actor.ownerId))
      .order("desc")
      .take(limit);
    return await Promise.all(events.map((event) => projectEvidence(ctx, actor.ownerId, event)));
  },
});
