import { mutation, query } from "./_generated/server";
import type { MutationCtx } from "./_generated/server";
import { v } from "convex/values";

import { requireActor, requireRegisteredDevice } from "./lib/auth";
import {
  requireOwnedCommand,
  requireOwnedEvent,
  requireOwnedProposal,
} from "./lib/data";
import { fail } from "./lib/errors";
import { clampLimit, isCurrentEvent } from "./lib/state";
import { appendTrace } from "./lib/tracing";
import { actorInputValidator } from "./lib/validators";

async function staleCommand(
  ctx: MutationCtx,
  command: Awaited<ReturnType<typeof requireOwnedCommand>>,
  reason: string,
) {
  const now = Date.now();
  await ctx.db.patch(command._id, {
    status: "STALE",
    staleReason: reason,
    finalAtMs: now,
  });
  const proposal = await requireOwnedProposal(ctx, command.ownerId, command.proposalId);
  if (proposal.status === "COMMITTED") {
    await ctx.db.patch(proposal._id, {
      status: "STALE",
      staleReason: reason,
      updatedAtMs: now,
    });
  }
  return await ctx.db.get(command._id);
}

async function revalidateCommand(
  ctx: MutationCtx,
  command: Awaited<ReturnType<typeof requireOwnedCommand>>,
) {
  const event = await requireOwnedEvent(ctx, command.ownerId, command.eventId);
  if (!isCurrentEvent(event.status)) {
    return { ok: false as const, reason: "NOTIFICATION_GONE", event };
  }
  if (event.version !== command.expectedEventVersion) {
    return { ok: false as const, reason: "EVENT_CHANGED", event };
  }
  if (!event.capabilities.includes(command.actionType)) {
    return { ok: false as const, reason: "ACTION_HANDLE_CHANGED", event };
  }
  return { ok: true as const, event };
}

export const forDevice = query({
  args: { actor: actorInputValidator, limit: v.optional(v.number()) },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    const limit = clampLimit(args.limit, 20, 50);
    const statuses = ["QUEUED", "CLAIMED", "EXECUTING"] as const;
    const batches = await Promise.all(
      statuses.map((status) =>
        ctx.db
          .query("commands")
          .withIndex("by_owner_device_status", (query) =>
            query
              .eq("ownerId", actor.ownerId)
              .eq("targetDeviceId", device.deviceId)
              .eq("status", status),
          )
          .order("asc")
          .take(limit),
      ),
    );
    const now = Date.now();
    return batches
      .flat()
      .filter((command) => command.expiresAtMs > now)
      .sort((left, right) => left.createdAtMs - right.createdAtMs)
      .slice(0, limit);
  },
});

export const claim = mutation({
  args: {
    actor: actorInputValidator,
    commandId: v.string(),
    claimToken: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    if (args.claimToken.length < 16 || args.claimToken.length > 256) {
      fail("INVALID_CLAIM_TOKEN", "Claim token must contain 16 to 256 characters.");
    }
    const command = await requireOwnedCommand(ctx, actor.ownerId, args.commandId);
    if (command.targetDeviceId !== device.deviceId) {
      fail("UNAUTHORIZED", "This command targets another device.");
    }
    if (command.status === "DISPATCHED" || command.status === "FAILED") {
      const receipt = await ctx.db
        .query("actionReceipts")
        .withIndex("by_owner_command", (query) =>
          query.eq("ownerId", actor.ownerId).eq("commandId", command.commandId),
        )
        .unique();
      return { claimed: false as const, reason: "ALREADY_COMPLETE", command, receipt };
    }
    if (command.status === "STALE" || command.status === "EXPIRED") {
      return { claimed: false as const, reason: command.status, command };
    }
    const now = Date.now();
    if (command.expiresAtMs <= now) {
      await ctx.db.patch(command._id, { status: "EXPIRED", finalAtMs: now });
      await appendTrace(ctx, {
        ownerId: actor.ownerId,
        mode: command.sourceMode,
        traceId: command.traceId,
        name: "COMMAND_EXPIRED",
        service: "TEXTUREFLOW_CORE",
        outcome: "TIMEOUT",
        correlation: {
          eventId: command.eventId,
          eventVersion: command.expectedEventVersion,
          proposalId: command.proposalId,
          commandId: command.commandId,
          deviceId: command.targetDeviceId,
        },
        attributes: { actionType: command.actionType },
        occurredAtMs: now,
      });
      return {
        claimed: false as const,
        reason: "EXPIRED",
        command: await ctx.db.get(command._id),
      };
    }
    const validation = await revalidateCommand(ctx, command);
    if (!validation.ok) {
      return {
        claimed: false as const,
        reason: validation.reason,
        command: await staleCommand(ctx, command, validation.reason),
      };
    }
    if (command.status === "CLAIMED" || command.status === "EXECUTING") {
      if (command.claimToken !== args.claimToken) {
        fail("COMMAND_ALREADY_CLAIMED", "Another persisted execution attempt owns this command.");
      }
      return { claimed: true as const, duplicate: true, command };
    }
    await ctx.db.patch(command._id, {
      status: "CLAIMED",
      claimToken: args.claimToken,
      claimedAtMs: now,
    });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: command.sourceMode,
      traceId: command.traceId,
      name: "COMMAND_CLAIMED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: command.eventId,
        eventVersion: command.expectedEventVersion,
        proposalId: command.proposalId,
        commandId: command.commandId,
        deviceId: command.targetDeviceId,
      },
      attributes: { actionType: command.actionType },
      occurredAtMs: now,
    });
    return {
      claimed: true as const,
      duplicate: false,
      command: await ctx.db.get(command._id),
    };
  },
});

export const startExecution = mutation({
  args: {
    actor: actorInputValidator,
    commandId: v.string(),
    claimToken: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    const command = await requireOwnedCommand(ctx, actor.ownerId, args.commandId);
    if (command.targetDeviceId !== device.deviceId || command.claimToken !== args.claimToken) {
      fail("UNAUTHORIZED", "The device does not own this command claim.");
    }
    if (command.status === "EXECUTING") {
      return { operation: "IDEMPOTENT" as const, command };
    }
    if (command.status !== "CLAIMED") {
      fail("INVALID_COMMAND_STATE", "Only a claimed command can start execution.");
    }
    const now = Date.now();
    if (command.expiresAtMs <= now) {
      await ctx.db.patch(command._id, { status: "EXPIRED", finalAtMs: now });
      await appendTrace(ctx, {
        ownerId: actor.ownerId,
        mode: command.sourceMode,
        traceId: command.traceId,
        name: "COMMAND_EXPIRED",
        service: "TEXTUREFLOW_CORE",
        outcome: "TIMEOUT",
        correlation: {
          eventId: command.eventId,
          eventVersion: command.expectedEventVersion,
          proposalId: command.proposalId,
          commandId: command.commandId,
          deviceId: command.targetDeviceId,
        },
        attributes: { actionType: command.actionType },
        occurredAtMs: now,
      });
      return {
        operation: "EXPIRED" as const,
        command: await ctx.db.get(command._id),
      };
    }
    const validation = await revalidateCommand(ctx, command);
    if (!validation.ok) {
      return {
        operation: "STALE" as const,
        command: await staleCommand(ctx, command, validation.reason),
      };
    }
    await ctx.db.patch(command._id, { status: "EXECUTING", executingAtMs: now });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: command.sourceMode,
      traceId: command.traceId,
      name: "ACTION_EXECUTION_STARTED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: command.eventId,
        eventVersion: command.expectedEventVersion,
        proposalId: command.proposalId,
        commandId: command.commandId,
        deviceId: command.targetDeviceId,
      },
      attributes: { actionType: command.actionType },
      occurredAtMs: now,
    });
    return {
      operation: "UPDATE" as const,
      command: await ctx.db.get(command._id),
    };
  },
});

export const get = query({
  args: { actor: actorInputValidator, commandId: v.string() },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const command = await requireOwnedCommand(ctx, actor.ownerId, args.commandId);
    if (actor.role === "DEVICE" && command.targetDeviceId !== actor.deviceId) {
      fail("UNAUTHORIZED", "This command targets another device.");
    }
    return command;
  },
});
