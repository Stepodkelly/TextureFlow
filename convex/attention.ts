import { query } from "./_generated/server";
import { v } from "convex/values";

import { requireActor } from "./lib/auth";
import { getOwnedDevice } from "./lib/data";
import { clampLimit, isDeviceFresh } from "./lib/state";
import { actorInputValidator, attentionLevelValidator } from "./lib/validators";

function publicEvent(event: {
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
  status: "ACTIVE" | "UPDATED" | "REMOVED";
  capabilities: Array<"REPLY" | "DISMISS" | "SNOOZE" | "MARK_READ" | "OPEN_APP">;
  priority: { score: number; level: "LOW" | "NORMAL" | "IMPORTANT" | "URGENT"; reason: string };
  sourceMode: "LIVE" | "REHEARSAL";
}) {
  return {
    contractVersion: event.contractVersion,
    eventId: event.eventId,
    deviceId: event.deviceId,
    app: event.app,
    sender: event.sender,
    conversationLabel: event.conversationLabel,
    body: event.body,
    postedAt: event.postedAt,
    updatedAt: event.updatedAt,
    version: event.version,
    status: event.status,
    capabilities: event.capabilities,
    priority: event.priority,
    sourceMode: event.sourceMode,
  };
}

function preferredSourceMode<T extends { sourceMode: "LIVE" | "REHEARSAL" }>(events: T[]) {
  return events.some((event) => event.sourceMode === "LIVE")
    ? ("LIVE" as const)
    : ("REHEARSAL" as const);
}

function actionableOrder(
  left: {
    capabilities: string[];
    priority: { score: number; level: "LOW" | "NORMAL" | "IMPORTANT" | "URGENT" };
    updatedAtMs: number;
  },
  right: {
    capabilities: string[];
    priority: { score: number; level: "LOW" | "NORMAL" | "IMPORTANT" | "URGENT" };
    updatedAtMs: number;
  },
) {
  const urgent = Number(right.priority.level === "URGENT")
    - Number(left.priority.level === "URGENT");
  if (urgent !== 0) return urgent;
  const replyable = Number(right.capabilities.includes("REPLY"))
    - Number(left.capabilities.includes("REPLY"));
  if (replyable !== 0) return replyable;
  return right.priority.score - left.priority.score || right.updatedAtMs - left.updatedAtMs;
}

export const list = query({
  args: {
    actor: actorInputValidator,
    limit: v.optional(v.number()),
    minimumLevel: v.optional(attentionLevelValidator),
  },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const limit = clampLimit(args.limit, 3, 20);
    const recent = await ctx.db
      .query("notificationEvents")
      .withIndex("by_owner_updated", (query) => query.eq("ownerId", actor.ownerId))
      .order("desc")
      .take(200);
    const rank = { LOW: 0, NORMAL: 1, IMPORTANT: 2, URGENT: 3 } as const;
    const minimumRank = args.minimumLevel ? rank[args.minimumLevel] : 0;
    const current = recent.filter(
      (event) => event.status === "ACTIVE" || event.status === "UPDATED",
    );
    const sourceMode = preferredSourceMode(current);
    const candidates = current
      .filter((event) => event.sourceMode === sourceMode)
      .filter((event) => rank[event.priority.level] >= minimumRank)
      .sort(actionableOrder)
      .slice(0, limit);

    const now = Date.now();
    return await Promise.all(
      candidates.map(async (event) => {
        const device = await getOwnedDevice(ctx, actor.ownerId, event.deviceId);
        const online =
          device?.status === "REHEARSAL" ||
          (device?.status === "ONLINE" && isDeviceFresh(device.lastSeenAtMs, now));
        return { ...publicEvent(event), deviceOnline: Boolean(online) };
      }),
    );
  },
});

export const weave = query({
  args: { actor: actorInputValidator, limit: v.optional(v.number()) },
  handler: async (ctx, args) => {
    const actor = await requireActor(ctx, args.actor, ["USER", "BRIDGE", "DEVICE"]);
    const limit = clampLimit(args.limit, 3, 10);
    const events = await ctx.db
      .query("notificationEvents")
      .withIndex("by_owner_updated", (query) => query.eq("ownerId", actor.ownerId))
      .order("desc")
      .take(100);
    const current = events.filter(
      (event) => event.status === "ACTIVE" || event.status === "UPDATED",
    );
    const sourceMode = preferredSourceMode(current);
    const groups = new Map<
      string,
      {
        personId?: string;
        displayName: string;
        events: ReturnType<typeof publicEvent>[];
        maximumScore: number;
        latestAtMs: number;
      }
    >();
    for (const event of current.filter((candidate) => candidate.sourceMode === sourceMode)) {
      const key = event.sender.personId ?? `${event.app.packageName}:${event.sender.displayName}`;
      const group = groups.get(key) ?? {
        personId: event.sender.personId,
        displayName: event.sender.displayName,
        events: [],
        maximumScore: 0,
        latestAtMs: 0,
      };
      group.events.push(publicEvent(event));
      group.maximumScore = Math.max(group.maximumScore, event.priority.score);
      group.latestAtMs = Math.max(group.latestAtMs, event.updatedAtMs);
      groups.set(key, group);
    }
    return [...groups.values()]
      .sort(
        (left, right) =>
          right.maximumScore - left.maximumScore || right.latestAtMs - left.latestAtMs,
      )
      .slice(0, limit)
      .map((group) => ({ ...group, events: group.events.slice(0, 5) }));
  },
});
