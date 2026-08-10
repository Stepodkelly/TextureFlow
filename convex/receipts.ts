import { mutation, query } from "./_generated/server";
import { v } from "convex/values";

import { requireActor, requireRegisteredDevice } from "./lib/auth";
import { requireOwnedCommand } from "./lib/data";
import { fail, rethrowDomain } from "./lib/errors";
import {
  CONTRACT_VERSION,
  expectedTextureCue,
  parseIsoTimestamp,
  receiptCommandStatus,
} from "./lib/state";
import { appendTrace } from "./lib/tracing";
import {
  actorInputValidator,
  receiptStatusValidator,
  textureCueValidator,
  textureErrorCodeValidator,
} from "./lib/validators";

export const complete = mutation({
  args: {
    actor: actorInputValidator,
    claimToken: v.string(),
    receipt: v.object({
      contractVersion: v.literal(1),
      receiptId: v.string(),
      commandId: v.string(),
      deviceId: v.string(),
      status: receiptStatusValidator,
      errorCode: v.optional(textureErrorCodeValidator),
      message: v.string(),
      deviceTimestamp: v.string(),
      textureCue: textureCueValidator,
      traceId: v.string(),
    }),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    if (args.receipt.deviceId !== device.deviceId) {
      fail("UNAUTHORIZED", "A device can only submit its own receipts.");
    }
    const command = await requireOwnedCommand(ctx, actor.ownerId, args.receipt.commandId);
    if (command.targetDeviceId !== device.deviceId) {
      fail("UNAUTHORIZED", "The receipt device does not own the command.");
    }
    if (command.traceId !== args.receipt.traceId) {
      fail("TRACE_MISMATCH", "Receipt and command trace IDs must match.");
    }
    if (command.sourceMode === "REHEARSAL") {
      fail("REHEARSAL_RECEIPT_FORBIDDEN", "Rehearsal commands cannot create execution receipts.");
    }
    if (args.receipt.textureCue !== expectedTextureCue(args.receipt.status)) {
      fail("INVALID_TEXTURE_CUE", "Receipt texture cue does not match its status.");
    }
    if (args.receipt.status === "FAILED" && !args.receipt.errorCode) {
      fail("MISSING_ERROR_CODE", "A failed receipt must include an error code.");
    }
    let deviceTimestampMs: number;
    try {
      deviceTimestampMs = parseIsoTimestamp(args.receipt.deviceTimestamp, "deviceTimestamp");
    } catch (error) {
      rethrowDomain(error);
    }
    if (!args.receipt.receiptId.trim() || !args.receipt.message.trim()) {
      fail("INVALID_RECEIPT", "Receipt ID and message are required.");
    }
    const existingForCommand = await ctx.db
      .query("actionReceipts")
      .withIndex("by_owner_command", (query) =>
        query.eq("ownerId", actor.ownerId).eq("commandId", command.commandId),
      )
      .unique();
    if (existingForCommand) {
      const identical =
        existingForCommand.receiptId === args.receipt.receiptId &&
        existingForCommand.status === args.receipt.status &&
        existingForCommand.errorCode === args.receipt.errorCode &&
        existingForCommand.message === args.receipt.message &&
        existingForCommand.deviceTimestamp === args.receipt.deviceTimestamp &&
        existingForCommand.textureCue === args.receipt.textureCue;
      if (!identical) {
        fail("RECEIPT_CONFLICT", "A command already has a different authoritative receipt.");
      }
      return { operation: "IDEMPOTENT" as const, receipt: existingForCommand };
    }
    if (
      !command.claimToken ||
      command.claimToken !== args.claimToken ||
      command.claimedAtMs === undefined ||
      command.claimedAtMs > command.expiresAtMs
    ) {
      fail(
        "INVALID_COMMAND_CLAIM",
        "An authoritative receipt requires the target device's unexpired persisted claim.",
      );
    }
    if (
      args.receipt.status === "DISPATCHED" &&
      command.status !== "CLAIMED" &&
      command.status !== "EXECUTING" &&
      command.status !== "STALE"
    ) {
      fail("INVALID_COMMAND_STATE", "The command cannot accept a dispatched receipt.");
    }
    const receiptById = await ctx.db
      .query("actionReceipts")
      .withIndex("by_owner_receipt", (query) =>
        query.eq("ownerId", actor.ownerId).eq("receiptId", args.receipt.receiptId),
      )
      .unique();
    if (receiptById) {
      fail("RECEIPT_ID_CONFLICT", "Receipt ID is already used by another command.");
    }

    const now = Date.now();
    const id = await ctx.db.insert("actionReceipts", {
      ownerId: actor.ownerId,
      contractVersion: CONTRACT_VERSION,
      receiptId: args.receipt.receiptId,
      commandId: command.commandId,
      deviceId: device.deviceId,
      status: args.receipt.status,
      errorCode: args.receipt.errorCode,
      message: args.receipt.message,
      deviceTimestamp: args.receipt.deviceTimestamp,
      deviceTimestampMs,
      textureCue: args.receipt.textureCue,
      traceId: args.receipt.traceId,
      receivedAtMs: now,
    });
    await ctx.db.patch(command._id, {
      status: receiptCommandStatus(args.receipt.status),
      finalAtMs: now,
    });
    const session = await ctx.db
      .query("voiceSessions")
      .withIndex("by_owner_session", (query) => query.eq("ownerId", actor.ownerId))
      .filter((query) => query.eq(query.field("activeProposalId"), command.proposalId))
      .first();
    if (session) {
      await ctx.db.patch(session._id, {
        status: "RECEIPT",
        activeProposalId: undefined,
        updatedAtMs: now,
      });
    }
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: command.sourceMode,
      traceId: command.traceId,
      spanId: `${command.traceId}:device-receipt:${args.receipt.receiptId}`,
      name: args.receipt.status === "DISPATCHED" ? "ACTION_DISPATCHED" : "ACTION_FAILED",
      service: "ANDROID_MOBILE",
      outcome: args.receipt.status === "DISPATCHED" ? "OK" : "ERROR",
      correlation: {
        eventId: command.eventId,
        eventVersion: command.expectedEventVersion,
        proposalId: command.proposalId,
        commandId: command.commandId,
        deviceId: command.targetDeviceId,
      },
      attributes: {
        receiptStatus: args.receipt.status,
        errorCode: args.receipt.errorCode ?? "NONE",
        actionType: command.actionType,
        receiptId: args.receipt.receiptId,
      },
      occurredAtMs: deviceTimestampMs,
    });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: command.sourceMode,
      traceId: command.traceId,
      spanId: `${command.traceId}:receipt-synced:${args.receipt.receiptId}`,
      parentSpanId: `${command.traceId}:device-receipt:${args.receipt.receiptId}`,
      name: "RECEIPT_SYNCED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: command.eventId,
        eventVersion: command.expectedEventVersion,
        proposalId: command.proposalId,
        commandId: command.commandId,
        deviceId: command.targetDeviceId,
      },
      attributes: {
        receiptStatus: args.receipt.status,
        receiptId: args.receipt.receiptId,
      },
      occurredAtMs: now,
    });
    return { operation: "INSERT" as const, receipt: await ctx.db.get(id) };
  },
});

export const getByCommand = query({
  args: { actor: actorInputValidator, commandId: v.string() },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const command = await requireOwnedCommand(ctx, actor.ownerId, args.commandId);
    if (actor.role === "DEVICE" && command.targetDeviceId !== actor.deviceId) {
      fail("UNAUTHORIZED", "This command targets another device.");
    }
    return await ctx.db
      .query("actionReceipts")
      .withIndex("by_owner_command", (query) =>
        query.eq("ownerId", actor.ownerId).eq("commandId", command.commandId),
      )
      .unique();
  },
});
