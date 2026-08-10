import type { MutationCtx, QueryCtx } from "../_generated/server";

import { fail } from "./errors";

export type ActorRole = "USER" | "BRIDGE" | "DEVICE";

export interface ActorInput {
  ownerId: string;
  role: ActorRole;
  deviceId?: string;
  token?: string;
}

export interface Actor {
  ownerId: string;
  role: ActorRole;
  deviceId?: string;
  subject: string;
  authentication: "OIDC" | "DEMO_TOKEN";
}

type AuthCtx = Pick<QueryCtx | MutationCtx, "auth">;

const ROLE_CLAIMS = ["textureflow_role", "https://textureflow.app/role"];
const OWNER_CLAIMS = ["textureflow_owner_id", "https://textureflow.app/owner_id"];
const DEVICE_CLAIMS = ["textureflow_device_id", "https://textureflow.app/device_id"];

function identityValue(identity: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = identity[key];
    if (typeof value === "string" && value.length > 0) {
      return value;
    }
  }
  return undefined;
}

function secureStringEqual(left: string, right: string): boolean {
  let mismatch = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    mismatch |= (left.charCodeAt(index) || 0) ^ (right.charCodeAt(index) || 0);
  }
  return mismatch === 0;
}

function expectedDemoToken(role: ActorRole): string | undefined {
  const runtime = globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  };
  const environment = runtime.process?.env;
  if (role === "DEVICE") {
    return environment?.TEXTUREFLOW_DEVICE_TOKEN;
  }
  if (role === "BRIDGE") {
    return environment?.TEXTUREFLOW_BRIDGE_TOKEN;
  }
  return environment?.TEXTUREFLOW_USER_TOKEN;
}

function normalizeRole(value: string | undefined): ActorRole {
  if (value === undefined) {
    return "USER";
  }
  if (value === "USER" || value === "BRIDGE" || value === "DEVICE") {
    return value;
  }
  fail("UNAUTHORIZED", "The authenticated identity has an unsupported TextureFlow role.");
}

export async function requireActor(
  ctx: AuthCtx,
  input: ActorInput,
  allowedRoles: readonly ActorRole[],
): Promise<Actor> {
  if (!input.ownerId.trim()) {
    fail("UNAUTHORIZED", "An owner ID is required.");
  }
  if (!allowedRoles.includes(input.role)) {
    fail("UNAUTHORIZED", `The ${input.role} actor cannot call this function.`);
  }

  const identity = await ctx.auth.getUserIdentity();
  if (identity !== null) {
    const claims = identity as unknown as Record<string, unknown>;
    const role = normalizeRole(identityValue(claims, ROLE_CLAIMS));
    const ownerId = identityValue(claims, OWNER_CLAIMS) ?? identity.tokenIdentifier;
    const deviceId = identityValue(claims, DEVICE_CLAIMS);

    if (role !== input.role || ownerId !== input.ownerId) {
      fail("UNAUTHORIZED", "Actor claims do not match the requested TextureFlow identity.");
    }
    if (role === "DEVICE" && (!deviceId || deviceId !== input.deviceId)) {
      fail("UNAUTHORIZED", "Device claims do not match the requested device.");
    }
    return {
      ownerId,
      role,
      deviceId,
      subject: identity.tokenIdentifier,
      authentication: "OIDC",
    };
  }

  const expected = expectedDemoToken(input.role);
  if (!expected || !input.token || !secureStringEqual(expected, input.token)) {
    fail("UNAUTHORIZED", "A valid authenticated identity or configured demo token is required.");
  }
  if (input.role === "DEVICE" && !input.deviceId) {
    fail("UNAUTHORIZED", "A device actor must include its device ID.");
  }

  return {
    ownerId: input.ownerId,
    role: input.role,
    deviceId: input.deviceId,
    subject: `demo:${input.role}:${input.ownerId}:${input.deviceId ?? "-"}`,
    authentication: "DEMO_TOKEN",
  };
}

export async function requireRegisteredDevice(
  ctx: QueryCtx | MutationCtx,
  actor: Actor,
) {
  if (actor.role !== "DEVICE" || !actor.deviceId) {
    fail("UNAUTHORIZED", "This operation requires a device identity.");
  }
  const device = await ctx.db
    .query("devices")
    .withIndex("by_owner_device", (query) =>
      query.eq("ownerId", actor.ownerId).eq("deviceId", actor.deviceId!),
    )
    .unique();
  if (!device || device.authSubject !== actor.subject) {
    fail("UNAUTHORIZED", "The device is not registered to this authenticated identity.");
  }
  return device;
}
