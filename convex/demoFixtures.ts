import { mutation, query } from "./_generated/server";
import type { MutationCtx } from "./_generated/server";
import { v } from "convex/values";

import { requireActor } from "./lib/auth";
import {
  ensureProvisionalPerson,
  getOwnedEvent,
  requireOwnedDevice,
  staleWorkForEvent,
  upsertAttentionAssessment,
  type StoredEventInput,
} from "./lib/data";
import { fail } from "./lib/errors";
import { eventFingerprint } from "./lib/state";
import { appendTrace } from "./lib/tracing";
import { actorInputValidator } from "./lib/validators";

const FIXTURES: Array<{ fixtureId: string; label: string; eventTemplate: StoredEventInput }> = [
  {
    fixtureId: "sam_downstairs",
    label: "Sam is waiting downstairs",
    eventTemplate: {
      contractVersion: 1,
      eventId: "evt_demo_sam",
      deviceId: "android_demo",
      app: { packageName: "com.whatsapp", label: "WhatsApp" },
      sender: { displayName: "Sam", personId: "person_sam" },
      conversationLabel: "Sam",
      body: "I'm downstairs. The door is locked.",
      postedAt: "2026-08-09T18:10:00-07:00",
      updatedAt: "2026-08-09T18:10:00-07:00",
      version: 1,
      status: "ACTIVE",
      capabilities: ["REPLY", "DISMISS", "SNOOZE"],
      priority: {
        score: 0.94,
        level: "URGENT",
        reason: "A close contact is waiting outside and cannot enter.",
      },
    },
  },
  {
    fixtureId: "maya_nine",
    label: "Maya asks about dinner",
    eventTemplate: {
      contractVersion: 1,
      eventId: "evt_demo_maya",
      deviceId: "android_demo",
      app: { packageName: "org.telegram.messenger", label: "Telegram" },
      sender: { displayName: "Maya", personId: "person_maya" },
      conversationLabel: "Maya",
      body: "Are we still meeting at nine?",
      postedAt: "2026-08-09T18:11:00-07:00",
      updatedAt: "2026-08-09T18:11:00-07:00",
      version: 1,
      status: "ACTIVE",
      capabilities: ["REPLY", "DISMISS"],
      priority: {
        score: 0.72,
        level: "IMPORTANT",
        reason: "A direct question from an important person needs a response.",
      },
    },
  },
];

async function seedPerson(
  ctx: MutationCtx,
  ownerId: string,
  input: {
    personId: string;
    displayName: string;
    importance: number;
    packageName: string;
    handle: string;
  },
) {
  const now = Date.now();
  const person = await ctx.db
    .query("persons")
    .withIndex("by_owner_person", (query) =>
      query.eq("ownerId", ownerId).eq("personId", input.personId),
    )
    .unique();
  if (!person) {
    await ctx.db.insert("persons", {
      ownerId,
      personId: input.personId,
      displayName: input.displayName,
      relationshipLabel: "DEMO_CONTACT",
      importance: input.importance,
      provisional: false,
      createdAtMs: now,
      updatedAtMs: now,
    });
  }
  const identityId = `demo:${input.packageName}:${input.personId}`;
  const identity = await ctx.db
    .query("identities")
    .withIndex("by_owner_identity", (query) =>
      query.eq("ownerId", ownerId).eq("identityId", identityId),
    )
    .unique();
  if (!identity) {
    await ctx.db.insert("identities", {
      ownerId,
      identityId,
      personId: input.personId,
      packageName: input.packageName,
      handle: input.handle,
      normalizedHandle: input.handle.toLocaleLowerCase("en-US"),
      verified: true,
      createdAtMs: now,
      updatedAtMs: now,
    });
  }
}

export const seed = mutation({
  args: { actor: actorInputValidator },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const now = Date.now();
    let inserted = 0;
    for (const fixture of FIXTURES) {
      const existing = await ctx.db
        .query("demoFixtures")
        .withIndex("by_owner_fixture", (query) =>
          query.eq("ownerId", actor.ownerId).eq("fixtureId", fixture.fixtureId),
        )
        .unique();
      if (existing) {
        await ctx.db.patch(existing._id, {
          label: fixture.label,
          eventTemplate: fixture.eventTemplate,
          updatedAtMs: now,
        });
      } else {
        await ctx.db.insert("demoFixtures", {
          ownerId: actor.ownerId,
          ...fixture,
          createdAtMs: now,
          updatedAtMs: now,
        });
        inserted += 1;
      }
    }
    await seedPerson(ctx, actor.ownerId, {
      personId: "person_sam",
      displayName: "Sam",
      importance: 0.95,
      packageName: "com.whatsapp",
      handle: "Sam",
    });
    await seedPerson(ctx, actor.ownerId, {
      personId: "person_maya",
      displayName: "Maya",
      importance: 0.8,
      packageName: "org.telegram.messenger",
      handle: "Maya",
    });
    return { total: FIXTURES.length, inserted };
  },
});

export const list = query({
  args: { actor: actorInputValidator },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    return await ctx.db
      .query("demoFixtures")
      .withIndex("by_owner_fixture", (query) => query.eq("ownerId", actor.ownerId))
      .take(25);
  },
});

export const inject = mutation({
  args: {
    actor: actorInputValidator,
    fixtureId: v.string(),
    targetDeviceId: v.string(),
    eventId: v.optional(v.string()),
    traceId: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const device = await requireOwnedDevice(ctx, actor.ownerId, args.targetDeviceId);
    if (device.status !== "REHEARSAL" || device.platform !== "SIMULATOR") {
      fail("REHEARSAL_DEVICE_REQUIRED", "Fixtures can only target a labeled rehearsal simulator.");
    }
    const fixture = await ctx.db
      .query("demoFixtures")
      .withIndex("by_owner_fixture", (query) =>
        query.eq("ownerId", actor.ownerId).eq("fixtureId", args.fixtureId),
      )
      .unique();
    if (!fixture) {
      fail("FIXTURE_NOT_FOUND", "Seed the requested demo fixture before injecting it.");
    }
    const eventId = args.eventId?.trim() || fixture.eventTemplate.eventId;
    const existing = await getOwnedEvent(ctx, actor.ownerId, eventId);
    const now = Date.now();
    const timestamp = new Date(now).toISOString();
    const version = existing ? existing.version + 1 : 1;
    const status = existing ? ("UPDATED" as const) : ("ACTIVE" as const);
    const normalized = {
      ...fixture.eventTemplate,
      eventId,
      deviceId: device.deviceId,
      postedAt: existing?.postedAt ?? timestamp,
      updatedAt: timestamp,
      version,
      status,
    };
    const values = {
      contractVersion: 1 as const,
      eventId,
      deviceId: device.deviceId,
      app: normalized.app,
      sender: normalized.sender,
      conversationLabel: normalized.conversationLabel,
      body: normalized.body,
      postedAt: normalized.postedAt,
      postedAtMs: Date.parse(normalized.postedAt),
      updatedAt: timestamp,
      updatedAtMs: now,
      version,
      status,
      capabilities: normalized.capabilities,
      priority: normalized.priority,
      sourceMode: "REHEARSAL" as const,
      fingerprint: eventFingerprint(normalized),
      syncedAtMs: now,
    };
    let eventDocumentId;
    if (existing) {
      if (existing.deviceId !== device.deviceId || existing.sourceMode !== "REHEARSAL") {
        fail("FIXTURE_EVENT_CONFLICT", "Fixture cannot replace an event from another source.");
      }
      await ctx.db.patch(existing._id, values);
      eventDocumentId = existing._id;
    } else {
      eventDocumentId = await ctx.db.insert("notificationEvents", {
        ownerId: actor.ownerId,
        ...values,
      });
    }
    await ensureProvisionalPerson(
      ctx,
      actor.ownerId,
      normalized.sender.personId,
      normalized.sender.displayName,
    );
    await upsertAttentionAssessment(ctx, {
      ownerId: actor.ownerId,
      eventId,
      eventVersion: version,
      priority: normalized.priority,
      source: "DETERMINISTIC",
    });
    const stale = existing
      ? await staleWorkForEvent(ctx, {
          ownerId: actor.ownerId,
          eventId,
          currentVersion: version,
          reason: "EVENT_CHANGED",
          traceId: args.traceId,
        })
      : { proposals: 0, commands: 0 };
    await appendTrace(ctx, {
      ownerId: actor.ownerId,
      mode: "REHEARSAL",
      traceId: args.traceId,
      name: "EVENT_INJECTED",
      service: "TEXTUREFLOW_CORE",
      correlation: {
        eventId,
        eventVersion: version,
        deviceId: device.deviceId,
      },
      attributes: { fixtureId: args.fixtureId },
      occurredAtMs: now,
    });
    return { event: await ctx.db.get(eventDocumentId), stale };
  },
});
