import type { MutationCtx, QueryCtx } from "../_generated/server";

import { fail } from "./errors";
import {
  shouldStaleCommand,
  shouldStaleProposal,
  type ActionCapability,
  type EventStatus,
} from "./state";
import { appendTrace } from "./tracing";

export async function getOwnedDevice(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  deviceId: string,
) {
  return await ctx.db
    .query("devices")
    .withIndex("by_owner_device", (query) =>
      query.eq("ownerId", ownerId).eq("deviceId", deviceId),
    )
    .unique();
}

export async function requireOwnedDevice(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  deviceId: string,
) {
  const device = await getOwnedDevice(ctx, ownerId, deviceId);
  if (!device) {
    fail("DEVICE_NOT_FOUND", "The target device is not registered.");
  }
  return device;
}

export async function getOwnedEvent(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  eventId: string,
) {
  return await ctx.db
    .query("notificationEvents")
    .withIndex("by_owner_event", (query) =>
      query.eq("ownerId", ownerId).eq("eventId", eventId),
    )
    .unique();
}

export async function requireOwnedEvent(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  eventId: string,
) {
  const event = await getOwnedEvent(ctx, ownerId, eventId);
  if (!event) {
    fail("EVENT_NOT_FOUND", "The notification event does not exist.");
  }
  return event;
}

export async function getOwnedProposal(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  proposalId: string,
) {
  return await ctx.db
    .query("actionProposals")
    .withIndex("by_owner_proposal", (query) =>
      query.eq("ownerId", ownerId).eq("proposalId", proposalId),
    )
    .unique();
}

export async function requireOwnedProposal(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  proposalId: string,
) {
  const proposal = await getOwnedProposal(ctx, ownerId, proposalId);
  if (!proposal) {
    fail("PROPOSAL_NOT_FOUND", "The proposal does not exist.");
  }
  return proposal;
}

export async function getOwnedCommand(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  commandId: string,
) {
  return await ctx.db
    .query("commands")
    .withIndex("by_owner_command", (query) =>
      query.eq("ownerId", ownerId).eq("commandId", commandId),
    )
    .unique();
}

export async function requireOwnedCommand(
  ctx: QueryCtx | MutationCtx,
  ownerId: string,
  commandId: string,
) {
  const command = await getOwnedCommand(ctx, ownerId, commandId);
  if (!command) {
    fail("COMMAND_NOT_FOUND", "The command does not exist.");
  }
  return command;
}

export async function ensureOwnerRecord(
  ctx: MutationCtx,
  ownerId: string,
  subject: string,
): Promise<void> {
  const existing = await ctx.db
    .query("users")
    .withIndex("by_owner", (query) => query.eq("ownerId", ownerId))
    .unique();
  const now = Date.now();
  if (existing) {
    await ctx.db.patch(existing._id, { updatedAtMs: now });
    return;
  }
  await ctx.db.insert("users", {
    ownerId,
    primarySubject: subject,
    createdAtMs: now,
    updatedAtMs: now,
  });
}

export async function ensureProvisionalPerson(
  ctx: MutationCtx,
  ownerId: string,
  personId: string | undefined,
  displayName: string,
): Promise<void> {
  if (!personId) {
    return;
  }
  const existing = await ctx.db
    .query("persons")
    .withIndex("by_owner_person", (query) =>
      query.eq("ownerId", ownerId).eq("personId", personId),
    )
    .unique();
  const now = Date.now();
  if (existing) {
    if (existing.provisional && existing.displayName !== displayName) {
      await ctx.db.patch(existing._id, { displayName, updatedAtMs: now });
    }
    return;
  }
  await ctx.db.insert("persons", {
    ownerId,
    personId,
    displayName,
    importance: 0.5,
    provisional: true,
    createdAtMs: now,
    updatedAtMs: now,
  });
}

export async function upsertAttentionAssessment(
  ctx: MutationCtx,
  input: {
    ownerId: string;
    eventId: string;
    eventVersion: number;
    priority: { score: number; level: "LOW" | "NORMAL" | "IMPORTANT" | "URGENT"; reason: string };
    source: "DEVICE" | "DETERMINISTIC" | "MODEL";
  },
): Promise<void> {
  const existing = await ctx.db
    .query("attentionAssessments")
    .withIndex("by_owner_event", (query) =>
      query.eq("ownerId", input.ownerId).eq("eventId", input.eventId),
    )
    .unique();
  const value = {
    eventVersion: input.eventVersion,
    score: input.priority.score,
    level: input.priority.level,
    reason: input.priority.reason,
    source: input.source,
    assessedAtMs: Date.now(),
  };
  if (existing) {
    await ctx.db.patch(existing._id, value);
  } else {
    await ctx.db.insert("attentionAssessments", {
      ownerId: input.ownerId,
      eventId: input.eventId,
      ...value,
    });
  }
}

export async function staleWorkForEvent(
  ctx: MutationCtx,
  input: {
    ownerId: string;
    eventId: string;
    currentVersion: number;
    reason: string;
    traceId: string;
  },
): Promise<{ proposals: number; commands: number }> {
  const proposals = await ctx.db
    .query("actionProposals")
    .withIndex("by_owner_event", (query) =>
      query.eq("ownerId", input.ownerId).eq("eventId", input.eventId),
    )
    .collect();
  let proposalCount = 0;
  let commandCount = 0;
  const now = Date.now();

  for (const proposal of proposals) {
    if (
      proposal.expectedEventVersion >= input.currentVersion ||
      !shouldStaleProposal(proposal.status)
    ) {
      continue;
    }
    await ctx.db.patch(proposal._id, {
      status: "STALE",
      staleReason: input.reason,
      updatedAtMs: now,
    });
    proposalCount += 1;
    await appendTrace(ctx, {
      ownerId: input.ownerId,
      mode: proposal.sourceMode,
      traceId: proposal.traceId,
      name: "PROPOSAL_STALE",
      service: "TEXTUREFLOW_CORE",
      outcome: "ERROR",
      correlation: {
        eventId: input.eventId,
        eventVersion: input.currentVersion,
        proposalId: proposal.proposalId,
        sessionId: proposal.sessionId,
        deviceId: proposal.targetDeviceId,
      },
      attributes: { reason: input.reason },
      occurredAtMs: now,
    });

    const commands = await ctx.db
      .query("commands")
      .withIndex("by_owner_proposal", (query) =>
        query.eq("ownerId", input.ownerId).eq("proposalId", proposal.proposalId),
      )
      .collect();
    for (const command of commands) {
      if (!shouldStaleCommand(command.status)) {
        continue;
      }
      await ctx.db.patch(command._id, {
        status: "STALE",
        staleReason: input.reason,
        finalAtMs: now,
      });
      commandCount += 1;
    }
  }

  return { proposals: proposalCount, commands: commandCount };
}

export interface StoredEventInput {
  contractVersion: 1;
  eventId: string;
  deviceId: string;
  app: { packageName: string; label: string };
  sender: { displayName: string; personId?: string };
  conversationLabel?: string;
  body?: string;
  postedAt: string;
  updatedAt: string;
  version: number;
  status: EventStatus;
  capabilities: ActionCapability[];
  priority: {
    score: number;
    level: "LOW" | "NORMAL" | "IMPORTANT" | "URGENT";
    reason: string;
  };
}
