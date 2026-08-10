import { mutation, query } from "./_generated/server";
import type { MutationCtx } from "./_generated/server";
import { v } from "convex/values";

import { requireActor } from "./lib/auth";
import {
  getOwnedProposal,
  requireOwnedDevice,
  requireOwnedEvent,
  requireOwnedProposal,
} from "./lib/data";
import { fail, rethrowDomain } from "./lib/errors";
import {
  COMMAND_MAX_TTL_MS,
  CONTRACT_VERSION,
  PROPOSAL_MAX_TTL_MS,
  commandIdFor,
  commandIdempotencyKey,
  confirmationGrantId,
  isCurrentEvent,
  isDeviceFresh,
  normalizeExpiry,
  payloadFingerprint,
  validateActionPayload,
  validateConfirmation,
  type ActionPayload,
} from "./lib/state";
import { appendTrace } from "./lib/tracing";
import {
  actionTypeValidator,
  actorInputValidator,
  payloadValidator,
} from "./lib/validators";

function validateProposalText(input: {
  proposalId: string;
  sessionId: string;
  spokenPreview: string;
  traceId: string;
}) {
  if (
    !input.proposalId.trim() ||
    !input.sessionId.trim() ||
    !input.spokenPreview.trim() ||
    !input.traceId.trim()
  ) {
    fail("INVALID_PROPOSAL", "Proposal, session, preview, and trace IDs are required.");
  }
  if (input.spokenPreview.length > 2_000) {
    fail("INVALID_PROPOSAL", "The spoken preview is too long.");
  }
}

async function upsertSession(
  ctx: MutationCtx,
  ownerId: string,
  sessionId: string,
  proposalId: string,
  expiresAtMs: number,
) {
  const session = await ctx.db
    .query("voiceSessions")
    .withIndex("by_owner_session", (query) =>
      query.eq("ownerId", ownerId).eq("sessionId", sessionId),
    )
    .unique();
  const now = Date.now();
  if (session) {
    await ctx.db.patch(session._id, {
      status: "AWAITING_CONFIRMATION",
      activeProposalId: proposalId,
      updatedAtMs: now,
      expiresAtMs,
    });
    return;
  }
  await ctx.db.insert("voiceSessions", {
    ownerId,
    sessionId,
    status: "AWAITING_CONFIRMATION",
    activeProposalId: proposalId,
    startedAtMs: now,
    updatedAtMs: now,
    expiresAtMs,
  });
}

export const create = mutation({
  args: {
    actor: actorInputValidator,
    proposalId: v.string(),
    sessionId: v.string(),
    eventId: v.string(),
    actionType: actionTypeValidator,
    payload: payloadValidator,
    spokenPreview: v.string(),
    expiresAt: v.string(),
    traceId: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    validateProposalText(args);
    try {
      validateActionPayload(args.actionType, args.payload as ActionPayload);
    } catch (error) {
      rethrowDomain(error);
    }
    const now = Date.now();
    let expiresAtMs: number;
    try {
      expiresAtMs = normalizeExpiry(args.expiresAt, now, PROPOSAL_MAX_TTL_MS);
    } catch (error) {
      rethrowDomain(error);
    }
    const event = await requireOwnedEvent(ctx, actor.ownerId, args.eventId);
    if (!isCurrentEvent(event.status)) {
      fail("NOTIFICATION_GONE", "The notification is no longer active.");
    }
    if (!event.capabilities.includes(args.actionType)) {
      fail("ACTION_NOT_AVAILABLE", `${args.actionType} is not available for this event.`);
    }
    const device = await requireOwnedDevice(ctx, actor.ownerId, event.deviceId);
    if (
      device.status !== "REHEARSAL" &&
      (device.status !== "ONLINE" || !isDeviceFresh(device.lastSeenAtMs, now))
    ) {
      fail("DEVICE_OFFLINE", "The target device is not fresh enough to prepare an action.");
    }

    const fingerprint = payloadFingerprint(args.payload as ActionPayload);
    const existing = await getOwnedProposal(ctx, actor.ownerId, args.proposalId);
    if (existing) {
      const identical =
        existing.sessionId === args.sessionId &&
        existing.eventId === args.eventId &&
        existing.actionType === args.actionType &&
        existing.payloadFingerprint === fingerprint &&
        existing.spokenPreview === args.spokenPreview.trim() &&
        existing.expiresAt === args.expiresAt &&
        existing.traceId === args.traceId;
      if (!identical) {
        fail("PROPOSAL_ID_CONFLICT", "The proposal ID already represents another action.");
      }
      return { operation: "IDEMPOTENT" as const, proposal: existing };
    }

    const id = await ctx.db.insert("actionProposals", {
      ownerId: actor.ownerId,
      contractVersion: CONTRACT_VERSION,
      proposalId: args.proposalId,
      sessionId: args.sessionId,
      eventId: event.eventId,
      expectedEventVersion: event.version,
      targetDeviceId: event.deviceId,
      actionType: args.actionType,
      payload: args.payload,
      payloadFingerprint: fingerprint,
      spokenPreview: args.spokenPreview.trim(),
      revision: 1,
      status: "PROPOSED",
      sourceMode: event.sourceMode,
      traceId: args.traceId,
      createdAt: new Date(now).toISOString(),
      createdAtMs: now,
      updatedAtMs: now,
      expiresAt: args.expiresAt,
      expiresAtMs,
    });
    await upsertSession(ctx, actor.ownerId, args.sessionId, args.proposalId, expiresAtMs);
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: event.sourceMode,
      traceId: args.traceId,
      name: "PROPOSAL_CREATED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: event.eventId,
        eventVersion: event.version,
        proposalId: args.proposalId,
        deviceId: event.deviceId,
        sessionId: args.sessionId,
      },
      attributes: {
        actionType: args.actionType,
        sourceApp: event.app.packageName,
      },
      occurredAtMs: now,
    });
    return { operation: "INSERT" as const, proposal: await ctx.db.get(id) };
  },
});

export const revise = mutation({
  args: {
    actor: actorInputValidator,
    proposalId: v.string(),
    expectedRevision: v.number(),
    payload: payloadValidator,
    spokenPreview: v.string(),
    expiresAt: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const proposal = await requireOwnedProposal(ctx, actor.ownerId, args.proposalId);
    if (proposal.status !== "PROPOSED" && proposal.status !== "REVISED") {
      fail("PROPOSAL_NOT_REVISABLE", "Only a pending proposal can be revised.");
    }
    if (proposal.revision !== args.expectedRevision) {
      fail("PROPOSAL_REVISION_CHANGED", "The proposal changed before this revision.");
    }
    if (!args.spokenPreview.trim() || args.spokenPreview.length > 2_000) {
      fail("INVALID_PROPOSAL", "A concise spoken preview is required.");
    }
    try {
      validateActionPayload(proposal.actionType, args.payload as ActionPayload);
    } catch (error) {
      rethrowDomain(error);
    }
    const now = Date.now();
    let expiresAtMs: number;
    try {
      expiresAtMs = normalizeExpiry(args.expiresAt, now, PROPOSAL_MAX_TTL_MS);
    } catch (error) {
      rethrowDomain(error);
    }
    const event = await requireOwnedEvent(ctx, actor.ownerId, proposal.eventId);
    if (!isCurrentEvent(event.status) || event.version !== proposal.expectedEventVersion) {
      await ctx.db.patch(proposal._id, {
        status: "STALE",
        staleReason: "EVENT_CHANGED",
        updatedAtMs: now,
      });
      return {
        ok: false as const,
        error: { code: "EVENT_CHANGED", message: "The notification changed before revision." },
      };
    }
    const device = await requireOwnedDevice(ctx, actor.ownerId, proposal.targetDeviceId);
    if (
      device.status !== "REHEARSAL" &&
      (device.status !== "ONLINE" || !isDeviceFresh(device.lastSeenAtMs, now))
    ) {
      fail("DEVICE_OFFLINE", "The target device is offline.");
    }
    const revision = proposal.revision + 1;
    await ctx.db.patch(proposal._id, {
      payload: args.payload,
      payloadFingerprint: payloadFingerprint(args.payload as ActionPayload),
      spokenPreview: args.spokenPreview.trim(),
      revision,
      status: "REVISED",
      updatedAtMs: now,
      expiresAt: args.expiresAt,
      expiresAtMs,
    });
    await upsertSession(ctx, actor.ownerId, proposal.sessionId, proposal.proposalId, expiresAtMs);
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: proposal.sourceMode,
      traceId: proposal.traceId,
      name: "PROPOSAL_REVISED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: proposal.eventId,
        eventVersion: proposal.expectedEventVersion,
        proposalId: proposal.proposalId,
        deviceId: proposal.targetDeviceId,
        sessionId: proposal.sessionId,
      },
      attributes: { revision, actionType: proposal.actionType },
      occurredAtMs: now,
    });
    return { ok: true as const, proposal: await ctx.db.get(proposal._id) };
  },
});

export const cancel = mutation({
  args: { actor: actorInputValidator, proposalId: v.string() },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const proposal = await requireOwnedProposal(ctx, actor.ownerId, args.proposalId);
    if (proposal.status === "CANCELLED") {
      return { operation: "IDEMPOTENT" as const, proposal };
    }
    if (proposal.status !== "PROPOSED" && proposal.status !== "REVISED") {
      fail("PROPOSAL_NOT_CANCELLABLE", "The proposal is no longer pending.");
    }
    const now = Date.now();
    await ctx.db.patch(proposal._id, { status: "CANCELLED", updatedAtMs: now });
    const session = await ctx.db
      .query("voiceSessions")
      .withIndex("by_owner_session", (query) =>
        query.eq("ownerId", actor.ownerId).eq("sessionId", proposal.sessionId),
      )
      .unique();
    if (session?.activeProposalId === proposal.proposalId) {
      await ctx.db.patch(session._id, {
        status: "PRESENTING",
        activeProposalId: undefined,
        updatedAtMs: now,
      });
    }
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: proposal.sourceMode,
      traceId: proposal.traceId,
      name: "PROPOSAL_CANCELLED",
      service: "TEXTUREFLOW_CORE",
      outcome: "SKIPPED",
      correlation: {
        eventId: proposal.eventId,
        eventVersion: proposal.expectedEventVersion,
        proposalId: proposal.proposalId,
        deviceId: proposal.targetDeviceId,
        sessionId: proposal.sessionId,
      },
      attributes: { actionType: proposal.actionType },
      occurredAtMs: now,
    });
    return { operation: "UPDATE" as const, proposal: await ctx.db.get(proposal._id) };
  },
});

export const confirm = mutation({
  args: {
    actor: actorInputValidator,
    proposalId: v.string(),
    sessionId: v.string(),
    expectedRevision: v.number(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const proposal = await requireOwnedProposal(ctx, actor.ownerId, args.proposalId);
    if (proposal.sessionId !== args.sessionId) {
      fail("SESSION_MISMATCH", "A proposal can only be confirmed in its originating session.");
    }

    if (proposal.status === "COMMITTED") {
      if (proposal.revision !== args.expectedRevision) {
        fail("PROPOSAL_REVISION_CHANGED", "The committed proposal revision does not match.");
      }
      const command = await ctx.db
        .query("commands")
        .withIndex("by_owner_proposal", (query) =>
          query.eq("ownerId", actor.ownerId).eq("proposalId", proposal.proposalId),
        )
        .unique();
      if (!command) {
        fail("INVARIANT_VIOLATION", "Committed proposal has no command.");
      }
      const receipt = await ctx.db
        .query("actionReceipts")
        .withIndex("by_owner_command", (query) =>
          query.eq("ownerId", actor.ownerId).eq("commandId", command.commandId),
        )
        .unique();
      return { ok: true as const, duplicate: true, proposal, command, receipt };
    }

    const event = await requireOwnedEvent(ctx, actor.ownerId, proposal.eventId);
    const device = await requireOwnedDevice(ctx, actor.ownerId, proposal.targetDeviceId);
    const now = Date.now();
    try {
      validateConfirmation({
        now,
        expectedRevision: args.expectedRevision,
        proposal,
        event,
        device,
      });
    } catch (error) {
      const code = error instanceof Error && "code" in error ? String(error.code) : "POLICY_BLOCKED";
      const message = error instanceof Error ? error.message : "Confirmation was blocked.";
      if (code === "PROPOSAL_EXPIRED") {
        await ctx.db.patch(proposal._id, { status: "EXPIRED", updatedAtMs: now });
      } else if (
        code === "EVENT_CHANGED" ||
        code === "NOTIFICATION_GONE" ||
        code === "ACTION_NOT_AVAILABLE"
      ) {
        await ctx.db.patch(proposal._id, {
          status: "STALE",
          staleReason: code,
          updatedAtMs: now,
        });
      }
      await appendTrace(ctx, {
        ownerId: actor.ownerId,
        mode: proposal.sourceMode,
        traceId: proposal.traceId,
        name: "CONFIRMATION_REJECTED",
        service: "TEXTUREFLOW_CORE",
        outcome: "ERROR",
        correlation: {
          eventId: proposal.eventId,
          eventVersion: proposal.expectedEventVersion,
          proposalId: proposal.proposalId,
          deviceId: proposal.targetDeviceId,
          sessionId: proposal.sessionId,
        },
        attributes: { errorCode: code, revision: proposal.revision },
        occurredAtMs: now,
      });
      return { ok: false as const, error: { code, message } };
    }

    const grantId = confirmationGrantId(proposal.proposalId, proposal.revision);
    const commandId = commandIdFor(proposal.proposalId, proposal.revision);
    const idempotencyKey = commandIdempotencyKey(proposal.proposalId, proposal.revision);
    const existingGrant = await ctx.db
      .query("confirmationGrants")
      .withIndex("by_owner_grant", (query) =>
        query.eq("ownerId", actor.ownerId).eq("grantId", grantId),
      )
      .unique();
    const existingCommand = await ctx.db
      .query("commands")
      .withIndex("by_owner_idempotency", (query) =>
        query.eq("ownerId", actor.ownerId).eq("idempotencyKey", idempotencyKey),
      )
      .unique();
    if (existingGrant || existingCommand) {
      fail("INVARIANT_VIOLATION", "Confirmation artifacts exist without a committed proposal.");
    }

    const commandExpiresAtMs = Math.min(
      proposal.expiresAtMs,
      now + COMMAND_MAX_TTL_MS,
    );
    const commandExpiresAt = new Date(commandExpiresAtMs).toISOString();
    await ctx.db.insert("confirmationGrants", {
      ownerId: actor.ownerId,
      grantId,
      proposalId: proposal.proposalId,
      proposalRevision: proposal.revision,
      sessionId: proposal.sessionId,
      payloadFingerprint: proposal.payloadFingerprint,
      confirmerSubject: actor.subject,
      confirmedAt: new Date(now).toISOString(),
      confirmedAtMs: now,
      expiresAtMs: commandExpiresAtMs,
    });
    const commandDocId = await ctx.db.insert("commands", {
      ownerId: actor.ownerId,
      contractVersion: CONTRACT_VERSION,
      commandId,
      proposalId: proposal.proposalId,
      proposalRevision: proposal.revision,
      confirmationGrantId: grantId,
      targetDeviceId: proposal.targetDeviceId,
      eventId: proposal.eventId,
      expectedEventVersion: proposal.expectedEventVersion,
      actionType: proposal.actionType,
      payload: proposal.payload,
      idempotencyKey,
      status: "QUEUED",
      sourceMode: proposal.sourceMode,
      traceId: proposal.traceId,
      createdAt: new Date(now).toISOString(),
      createdAtMs: now,
      expiresAt: commandExpiresAt,
      expiresAtMs: commandExpiresAtMs,
    });
    // CONFIRMED is intentionally not observable: the grant and queued command
    // commit in this same transaction, so the durable proposal state is COMMITTED.
    await ctx.db.patch(proposal._id, {
      status: "COMMITTED",
      confirmedAtMs: now,
      updatedAtMs: now,
    });
    const session = await ctx.db
      .query("voiceSessions")
      .withIndex("by_owner_session", (query) =>
        query.eq("ownerId", actor.ownerId).eq("sessionId", proposal.sessionId),
      )
      .unique();
    if (session) {
      await ctx.db.patch(session._id, { status: "EXECUTING", updatedAtMs: now });
    }
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: proposal.sourceMode,
      traceId: proposal.traceId,
      name: "PROPOSAL_CONFIRMED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: proposal.eventId,
        eventVersion: proposal.expectedEventVersion,
        proposalId: proposal.proposalId,
        commandId,
        deviceId: proposal.targetDeviceId,
        sessionId: proposal.sessionId,
      },
      attributes: { revision: proposal.revision, actionType: proposal.actionType },
      occurredAtMs: now,
    });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: proposal.sourceMode,
      traceId: proposal.traceId,
      name: "COMMAND_QUEUED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: proposal.eventId,
        eventVersion: proposal.expectedEventVersion,
        proposalId: proposal.proposalId,
        commandId,
        deviceId: proposal.targetDeviceId,
        sessionId: proposal.sessionId,
      },
      attributes: { actionType: proposal.actionType },
      occurredAtMs: now,
    });
    return {
      ok: true as const,
      duplicate: false,
      proposal: await ctx.db.get(proposal._id),
      command: await ctx.db.get(commandDocId),
      receipt: null,
    };
  },
});

export const get = query({
  args: { actor: actorInputValidator, proposalId: v.string() },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    return await requireOwnedProposal(ctx, actor.ownerId, args.proposalId);
  },
});
