import { mutation, query } from "./_generated/server";
import { v } from "convex/values";

import { requireActor, requireRegisteredDevice } from "./lib/auth";
import {
  ensureProvisionalPerson,
  getOwnedEvent,
  requireOwnedEvent,
  staleWorkForEvent,
  upsertAttentionAssessment,
} from "./lib/data";
import { fail, rethrowDomain } from "./lib/errors";
import {
  eventFingerprint,
  normalizeCapabilities,
  parseIsoTimestamp,
  validateEventAdvance,
} from "./lib/state";
import { appendTrace } from "./lib/tracing";
import { actorInputValidator, notificationEventInputValidator } from "./lib/validators";

function validateEventContent(event: {
  eventId: string;
  deviceId: string;
  app: { packageName: string; label: string };
  sender: { displayName: string };
  priority: { score: number; reason: string };
}) {
  if (
    !event.eventId.trim() ||
    !event.deviceId.trim() ||
    !event.app.packageName.trim() ||
    !event.app.label.trim() ||
    !event.sender.displayName.trim() ||
    !event.priority.reason.trim()
  ) {
    fail("INVALID_EVENT", "Event identifiers, app, sender, and priority reason are required.");
  }
  if (event.priority.score < 0 || event.priority.score > 1) {
    fail("INVALID_EVENT", "Priority score must be between zero and one.");
  }
}

export const upsert = mutation({
  args: {
    actor: actorInputValidator,
    event: notificationEventInputValidator,
    traceId: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    if (args.event.deviceId !== device.deviceId) {
      fail("UNAUTHORIZED", "A device can only upload its own notification events.");
    }
    if (args.event.status === "REMOVED") {
      fail("INVALID_EVENT_TRANSITION", "Use events:markRemoved to remove a notification.");
    }
    validateEventContent(args.event);

    const postedAtMs = parseIsoTimestamp(args.event.postedAt, "postedAt");
    const updatedAtMs = parseIsoTimestamp(args.event.updatedAt, "updatedAt");
    if (updatedAtMs < postedAtMs) {
      fail("INVALID_EVENT", "updatedAt cannot be before postedAt.");
    }
    const existing = await getOwnedEvent(ctx, actor.ownerId, args.event.eventId);
    if (existing && updatedAtMs < existing.updatedAtMs) {
      fail("INVALID_EVENT", "A newer event version cannot move updatedAt backwards.");
    }
    const capabilities = normalizeCapabilities(args.event.capabilities);
    const normalizedEvent = { ...args.event, capabilities };
    const fingerprint = eventFingerprint(normalizedEvent);
    let operation: "INSERT" | "IDEMPOTENT" | "UPDATE";
    try {
      operation = validateEventAdvance(
        existing ? { version: existing.version, fingerprint: existing.fingerprint } : null,
        { version: args.event.version, fingerprint },
      );
    } catch (error) {
      rethrowDomain(error);
    }
    if (existing && existing.deviceId !== device.deviceId) {
      fail("EVENT_DEVICE_MISMATCH", "An event cannot move between devices.");
    }
    if (operation === "IDEMPOTENT") {
      await ctx.db.patch(device._id, {
        lastSeenAt: new Date().toISOString(),
        lastSeenAtMs: Date.now(),
        updatedAtMs: Date.now(),
      });
      return { operation, event: existing, stale: { proposals: 0, commands: 0 } };
    }

    const now = Date.now();
    const values = {
      contractVersion: 1 as const,
      eventId: args.event.eventId,
      deviceId: args.event.deviceId,
      app: args.event.app,
      sender: args.event.sender,
      conversationLabel: args.event.conversationLabel,
      body: args.event.body,
      postedAt: args.event.postedAt,
      postedAtMs,
      updatedAt: args.event.updatedAt,
      updatedAtMs,
      version: args.event.version,
      status: args.event.status,
      capabilities,
      priority: args.event.priority,
      sourceMode: "LIVE" as const,
      fingerprint,
      syncedAtMs: now,
    };
    let event;
    if (existing) {
      await ctx.db.patch(existing._id, values);
      event = await ctx.db.get(existing._id);
    } else {
      const id = await ctx.db.insert("notificationEvents", {
        ownerId: actor.ownerId,
        ...values,
      });
      event = await ctx.db.get(id);
    }

    await ensureProvisionalPerson(
      ctx,
      actor.ownerId,
      args.event.sender.personId,
      args.event.sender.displayName,
    );
    await upsertAttentionAssessment(ctx, {
      ownerId: actor.ownerId,
      eventId: args.event.eventId,
      eventVersion: args.event.version,
      priority: args.event.priority,
      source: "DEVICE",
    });
    const stale =
      operation === "UPDATE"
        ? await staleWorkForEvent(ctx, {
            ownerId: actor.ownerId,
            eventId: args.event.eventId,
            currentVersion: args.event.version,
            reason: "EVENT_CHANGED",
            traceId: args.traceId,
          })
        : { proposals: 0, commands: 0 };
    await ctx.db.patch(device._id, {
      status: device.status === "REHEARSAL" ? "REHEARSAL" : "ONLINE",
      lastSeenAt: new Date(now).toISOString(),
      lastSeenAtMs: now,
      updatedAtMs: now,
    });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: "LIVE",
      traceId: args.traceId,
      name: operation === "INSERT" ? "EVENT_SYNCED" : "EVENT_UPDATED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: args.event.eventId,
        eventVersion: args.event.version,
        deviceId: device.deviceId,
      },
      attributes: { sourceApp: args.event.app.packageName },
      occurredAtMs: now,
    });
    return { operation, event, stale };
  },
});

export const markRemoved = mutation({
  args: {
    actor: actorInputValidator,
    eventId: v.string(),
    version: v.number(),
    updatedAt: v.string(),
    traceId: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    const event = await requireOwnedEvent(ctx, actor.ownerId, args.eventId);
    if (event.deviceId !== device.deviceId) {
      fail("UNAUTHORIZED", "A device can only remove its own notification events.");
    }
    const updatedAtMs = parseIsoTimestamp(args.updatedAt, "updatedAt");
    if (updatedAtMs < event.updatedAtMs) {
      fail("INVALID_EVENT", "Removal cannot move updatedAt backwards.");
    }
    if (event.status === "REMOVED" && args.version === event.version) {
      return { operation: "IDEMPOTENT" as const, event, stale: { proposals: 0, commands: 0 } };
    }
    if (!Number.isInteger(args.version) || args.version <= event.version) {
      fail("EVENT_VERSION_REGRESSION", "Removal must advance the event version.");
    }
    const now = Date.now();
    const removedShape = {
      contractVersion: 1 as const,
      eventId: event.eventId,
      deviceId: event.deviceId,
      app: event.app,
      sender: event.sender,
      conversationLabel: event.conversationLabel,
      body: event.body,
      postedAt: event.postedAt,
      updatedAt: args.updatedAt,
      version: args.version,
      status: "REMOVED" as const,
      capabilities: [],
      priority: event.priority,
    };
    await ctx.db.patch(event._id, {
      status: "REMOVED",
      version: args.version,
      capabilities: [],
      updatedAt: args.updatedAt,
      updatedAtMs,
      fingerprint: eventFingerprint(removedShape),
      syncedAtMs: now,
    });
    const stale = await staleWorkForEvent(ctx, {
      ownerId: actor.ownerId,
      eventId: event.eventId,
      currentVersion: args.version,
      reason: "NOTIFICATION_GONE",
      traceId: args.traceId,
    });
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: "LIVE",
      traceId: args.traceId,
      name: "EVENT_REMOVED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId: event.eventId,
        eventVersion: args.version,
        deviceId: device.deviceId,
      },
      attributes: { sourceApp: event.app.packageName },
      occurredAtMs: now,
    });
    return { operation: "UPDATE" as const, event: await ctx.db.get(event._id), stale };
  },
});

export const get = query({
  args: { actor: actorInputValidator, eventId: v.string() },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    return await requireOwnedEvent(ctx, actor.ownerId, args.eventId);
  },
});
