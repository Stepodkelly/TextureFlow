import { mutation, query } from "./_generated/server";
import { v } from "convex/values";

import { requireActor } from "./lib/auth";
import { fail } from "./lib/errors";
import { clampLimit } from "./lib/state";
import { actorInputValidator } from "./lib/validators";

function normalizeHandle(value: string): string {
  return value.trim().toLocaleLowerCase("en-US");
}

export const upsert = mutation({
  args: {
    actor: actorInputValidator,
    personId: v.string(),
    displayName: v.string(),
    relationshipLabel: v.optional(v.string()),
    importance: v.number(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    if (!args.personId.trim() || !args.displayName.trim()) {
      fail("INVALID_PERSON", "Person ID and display name are required.");
    }
    if (args.importance < 0 || args.importance > 1) {
      fail("INVALID_PERSON", "Person importance must be between zero and one.");
    }
    const existing = await ctx.db
      .query("persons")
      .withIndex("by_owner_person", (query) =>
        query.eq("ownerId", actor.ownerId).eq("personId", args.personId),
      )
      .unique();
    const now = Date.now();
    const values = {
      displayName: args.displayName.trim(),
      relationshipLabel: args.relationshipLabel?.trim() || undefined,
      importance: args.importance,
      provisional: false,
      updatedAtMs: now,
    };
    if (existing) {
      await ctx.db.patch(existing._id, values);
      return await ctx.db.get(existing._id);
    }
    const id = await ctx.db.insert("persons", {
      ownerId: actor.ownerId,
      personId: args.personId,
      ...values,
      createdAtMs: now,
    });
    return await ctx.db.get(id);
  },
});

export const addIdentity = mutation({
  args: {
    actor: actorInputValidator,
    identityId: v.string(),
    personId: v.string(),
    packageName: v.string(),
    handle: v.string(),
    verified: v.boolean(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE"]);
    const person = await ctx.db
      .query("persons")
      .withIndex("by_owner_person", (query) =>
        query.eq("ownerId", actor.ownerId).eq("personId", args.personId),
      )
      .unique();
    if (!person) {
      fail("PERSON_NOT_FOUND", "Identity must belong to an existing person.");
    }
    const normalizedHandle = normalizeHandle(args.handle);
    if (!args.identityId.trim() || !args.packageName.trim() || !normalizedHandle) {
      fail("INVALID_IDENTITY", "Identity ID, package, and handle are required.");
    }
    const collision = await ctx.db
      .query("identities")
      .withIndex("by_owner_package_handle", (query) =>
        query
          .eq("ownerId", actor.ownerId)
          .eq("packageName", args.packageName)
          .eq("normalizedHandle", normalizedHandle),
      )
      .unique();
    if (collision && collision.personId !== args.personId) {
      fail("IDENTITY_CONFLICT", "This app identity is already bound to another person.");
    }
    const existing = await ctx.db
      .query("identities")
      .withIndex("by_owner_identity", (query) =>
        query.eq("ownerId", actor.ownerId).eq("identityId", args.identityId),
      )
      .unique();
    if (existing && existing.personId !== args.personId) {
      fail("IDENTITY_CONFLICT", "Explicit identity merge approval is required.");
    }
    const now = Date.now();
    const values = {
      personId: args.personId,
      packageName: args.packageName,
      handle: args.handle.trim(),
      normalizedHandle,
      verified: args.verified,
      updatedAtMs: now,
    };
    if (existing) {
      await ctx.db.patch(existing._id, values);
      return await ctx.db.get(existing._id);
    }
    const id = await ctx.db.insert("identities", {
      ownerId: actor.ownerId,
      identityId: args.identityId,
      ...values,
      createdAtMs: now,
    });
    return await ctx.db.get(id);
  },
});

export const context = query({
  args: {
    actor: actorInputValidator,
    personId: v.optional(v.string()),
    displayName: v.optional(v.string()),
    eventLimit: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    if (Boolean(args.personId) === Boolean(args.displayName)) {
      fail("INVALID_PERSON_LOOKUP", "Provide exactly one of personId or displayName.");
    }
    const people = args.personId
      ? await ctx.db
          .query("persons")
          .withIndex("by_owner_person", (query) =>
            query.eq("ownerId", actor.ownerId).eq("personId", args.personId!),
          )
          .take(2)
      : await ctx.db
          .query("persons")
          .withIndex("by_owner_display_name", (query) =>
            query.eq("ownerId", actor.ownerId).eq("displayName", args.displayName!),
          )
          .take(3);
    if (people.length === 0) {
      fail("PERSON_NOT_FOUND", "No matching person was found.");
    }
    if (people.length > 1) {
      return {
        ambiguous: true,
        candidates: people.map((person) => ({
          personId: person.personId,
          displayName: person.displayName,
          relationshipLabel: person.relationshipLabel,
        })),
      };
    }
    const person = people[0];
    const eventLimit = clampLimit(args.eventLimit, 5, 10);
    const [identities, events] = await Promise.all([
      ctx.db
        .query("identities")
        .withIndex("by_owner_person", (query) =>
          query.eq("ownerId", actor.ownerId).eq("personId", person.personId),
        )
        .take(20),
      ctx.db
        .query("notificationEvents")
        .withIndex("by_owner_person_updated", (query) =>
          query.eq("ownerId", actor.ownerId).eq("sender.personId", person.personId),
        )
        .order("desc")
        .take(eventLimit),
    ]);
    return {
      ambiguous: false,
      person: {
        personId: person.personId,
        displayName: person.displayName,
        relationshipLabel: person.relationshipLabel,
        importance: person.importance,
        provisional: person.provisional,
      },
      identities: identities.map((identity) => ({
        identityId: identity.identityId,
        packageName: identity.packageName,
        handle: identity.handle,
        verified: identity.verified,
      })),
      recentEvents: events.map((event) => ({
        eventId: event.eventId,
        app: event.app,
        body: event.body,
        updatedAt: event.updatedAt,
        version: event.version,
        status: event.status,
        priority: event.priority,
        capabilities: event.capabilities,
        sourceMode: event.sourceMode,
      })),
    };
  },
});
