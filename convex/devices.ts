import { mutation, query } from "./_generated/server";
import { v } from "convex/values";

import { requireActor, requireRegisteredDevice } from "./lib/auth";
import { ensureOwnerRecord, getOwnedDevice } from "./lib/data";
import { fail } from "./lib/errors";
import { isDeviceFresh, parseIsoTimestamp } from "./lib/state";
import { appendTrace } from "./lib/tracing";
import {
  actorInputValidator,
  deviceStatusValidator,
  platformValidator,
} from "./lib/validators";

export const register = mutation({
  args: {
    actor: actorInputValidator,
    deviceId: v.string(),
    deviceAuthSubject: v.optional(v.string()),
    displayName: v.string(),
    platform: platformValidator,
    status: deviceStatusValidator,
    appVersion: v.string(),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    if (!args.deviceId.trim() || !args.displayName.trim() || !args.appVersion.trim()) {
      fail("INVALID_DEVICE", "Device ID, display name, and app version are required.");
    }
    if (actor.role === "DEVICE" && actor.deviceId !== args.deviceId) {
      fail("UNAUTHORIZED", "A device can only register itself.");
    }
    const authSubject =
      actor.role === "DEVICE" ? actor.subject : args.deviceAuthSubject?.trim();
    if (!authSubject) {
      fail("INVALID_DEVICE", "User and bridge provisioning requires a device auth subject.");
    }
    if (args.status === "REHEARSAL" && args.platform !== "SIMULATOR") {
      fail("INVALID_DEVICE", "Only simulator devices can register as rehearsal devices.");
    }

    await ensureOwnerRecord(ctx, actor.ownerId, actor.subject);
    const existing = await getOwnedDevice(ctx, actor.ownerId, args.deviceId);
    const now = Date.now();
    const values = {
      authSubject,
      displayName: args.displayName.trim(),
      platform: args.platform,
      status: args.status,
      appVersion: args.appVersion.trim(),
      contractVersion: 1 as const,
      lastSeenAt: new Date(now).toISOString(),
      lastSeenAtMs: now,
      updatedAtMs: now,
    };
    if (existing) {
      if (existing.authSubject !== authSubject) {
        fail("DEVICE_ALREADY_REGISTERED", "The device is bound to another auth subject.");
      }
      await ctx.db.patch(existing._id, values);
      return await ctx.db.get(existing._id);
    }
    const id = await ctx.db.insert("devices", {
      ownerId: actor.ownerId,
      deviceId: args.deviceId,
      ...values,
      createdAtMs: now,
    });
    return await ctx.db.get(id);
  },
});

export const heartbeat = mutation({
  args: {
    actor: actorInputValidator,
    appVersion: v.string(),
    deviceTimestamp: v.string(),
    traceId: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    parseIsoTimestamp(args.deviceTimestamp, "deviceTimestamp");
    const now = Date.now();
    await ctx.db.patch(device._id, {
      status: device.status === "REHEARSAL" ? "REHEARSAL" : "ONLINE",
      appVersion: args.appVersion,
      lastSeenAt: args.deviceTimestamp,
      lastSeenAtMs: now,
      updatedAtMs: now,
    });
    if (args.traceId) {
      await appendTrace(ctx, {
        ownerId: actor.ownerId,
        mode: device.status === "REHEARSAL" ? "REHEARSAL" : "LIVE",
        traceId: args.traceId,
        name: "DEVICE_HEARTBEAT",
        service: "TEXTUREFLOW_CORE",
        correlation: { deviceId: device.deviceId },
        attributes: { appVersion: args.appVersion },
        occurredAtMs: now,
      });
    }
    return { acceptedAt: new Date(now).toISOString(), deviceId: device.deviceId };
  },
});

export const setOffline = mutation({
  args: { actor: actorInputValidator },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["DEVICE"]);
    const device = await requireRegisteredDevice(ctx, actor);
    if (device.status !== "REHEARSAL") {
      await ctx.db.patch(device._id, { status: "OFFLINE", updatedAtMs: Date.now() });
    }
    return { deviceId: device.deviceId, status: device.status === "REHEARSAL" ? "REHEARSAL" : "OFFLINE" };
  },
});

export const list = query({
  args: { actor: actorInputValidator },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const devices = await ctx.db
      .query("devices")
      .withIndex("by_owner_last_seen", (query) => query.eq("ownerId", actor.ownerId))
      .order("desc")
      .take(25);
    if (actor.role === "DEVICE") {
      return devices.filter((device) => device.deviceId === actor.deviceId);
    }
    return devices;
  },
});

export const status = query({
  args: { actor: actorInputValidator },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const [devices, events, proposals] = await Promise.all([
      ctx.db
        .query("devices")
        .withIndex("by_owner_last_seen", (query) => query.eq("ownerId", actor.ownerId))
        .order("desc")
        .take(25),
      ctx.db
        .query("notificationEvents")
        .withIndex("by_owner_updated", (query) => query.eq("ownerId", actor.ownerId))
        .order("desc")
        .take(200),
      ctx.db
        .query("actionProposals")
        .withIndex("by_owner_proposal", (query) => query.eq("ownerId", actor.ownerId))
        .take(200),
    ]);
    const visibleDevices =
      actor.role === "DEVICE"
        ? devices.filter((device) => device.deviceId === actor.deviceId)
        : devices;
    const device = visibleDevices[0];
    const now = Date.now();
    const online = Boolean(
      device &&
        (device.status === "REHEARSAL" ||
          (device.status === "ONLINE" && isDeviceFresh(device.lastSeenAtMs, now))),
    );

    return {
      mode: device?.status === "REHEARSAL" ? ("REHEARSAL" as const) : ("LIVE" as const),
      bridge: "ONLINE" as const,
      device: {
        deviceId: device?.deviceId ?? "unregistered",
        label: device?.displayName ?? "No Android device",
        online,
        stale: Boolean(device && !online),
        lastSeenAt: device?.lastSeenAt ?? new Date(0).toISOString(),
      },
      activeEventCount: events.filter(
        (event) => event.status === "ACTIVE" || event.status === "UPDATED",
      ).length,
      pendingProposalCount: proposals.filter(
        (proposal) => proposal.status === "PROPOSED" || proposal.status === "REVISED",
      ).length,
    };
  },
});
