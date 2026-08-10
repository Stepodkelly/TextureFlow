export const CONTRACT_VERSION = 1 as const;

export const DEVICE_STALE_AFTER_MS = 45_000;
export const PROPOSAL_MAX_TTL_MS = 120_000;
export const COMMAND_MAX_TTL_MS = 60_000;

export const EVENT_STATUSES = ["ACTIVE", "UPDATED", "REMOVED"] as const;
export const ACTION_TYPES = ["REPLY", "DISMISS", "SNOOZE"] as const;
export const ACTION_CAPABILITIES = [
  ...ACTION_TYPES,
  "MARK_READ",
  "OPEN_APP",
] as const;
export const PROPOSAL_STATUSES = [
  "PROPOSED",
  "REVISED",
  "CONFIRMED",
  "COMMITTED",
  "CANCELLED",
  "EXPIRED",
  "STALE",
] as const;
export const COMMAND_STATUSES = [
  "QUEUED",
  "CLAIMED",
  "EXECUTING",
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE",
] as const;
export const RECEIPT_STATUSES = [
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE",
] as const;

export type EventStatus = (typeof EVENT_STATUSES)[number];
export type ActionType = (typeof ACTION_TYPES)[number];
export type ActionCapability = (typeof ACTION_CAPABILITIES)[number];
export type ProposalStatus = (typeof PROPOSAL_STATUSES)[number];
export type CommandStatus = (typeof COMMAND_STATUSES)[number];
export type ReceiptStatus = (typeof RECEIPT_STATUSES)[number];
export type Primitive = string | number | boolean;
export type ActionPayload = Record<string, Primitive>;

export class DomainInvariantError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "DomainInvariantError";
    this.code = code;
  }
}

export function invariant(
  condition: unknown,
  code: string,
  message: string,
): asserts condition {
  if (!condition) {
    throw new DomainInvariantError(code, message);
  }
}

export function parseIsoTimestamp(value: string, fieldName: string): number {
  const parsed = Date.parse(value);
  invariant(Number.isFinite(parsed), "INVALID_TIMESTAMP", `${fieldName} must be ISO-8601.`);
  return parsed;
}

export function normalizeExpiry(
  expiresAt: string,
  now: number,
  maximumTtlMs: number,
): number {
  const expiresAtMs = parseIsoTimestamp(expiresAt, "expiresAt");
  invariant(expiresAtMs > now, "ALREADY_EXPIRED", "Expiry must be in the future.");
  invariant(
    expiresAtMs - now <= maximumTtlMs,
    "EXPIRY_TOO_LONG",
    `Expiry cannot be more than ${maximumTtlMs}ms in the future.`,
  );
  return expiresAtMs;
}

export function isCurrentEvent(status: EventStatus): boolean {
  return status === "ACTIVE" || status === "UPDATED";
}

export function normalizeCapabilities(
  capabilities: readonly ActionCapability[],
): ActionCapability[] {
  const order = new Map(ACTION_CAPABILITIES.map((capability, index) => [capability, index]));
  return [...new Set(capabilities)].sort(
    (left, right) => order.get(left)! - order.get(right)!,
  );
}

export function isDeviceFresh(
  lastSeenAtMs: number,
  now: number,
  thresholdMs = DEVICE_STALE_AFTER_MS,
): boolean {
  return lastSeenAtMs <= now && now - lastSeenAtMs <= thresholdMs;
}

export function shouldStaleProposal(status: ProposalStatus): boolean {
  return (
    status === "PROPOSED" ||
    status === "REVISED" ||
    status === "CONFIRMED" ||
    status === "COMMITTED"
  );
}

export function shouldStaleCommand(status: CommandStatus): boolean {
  return status === "QUEUED" || status === "CLAIMED" || status === "EXECUTING";
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(stableValue);
  }
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, stableValue(item)]),
    );
  }
  return value;
}

export function canonicalize(value: unknown): string {
  return JSON.stringify(stableValue(value));
}

export function payloadFingerprint(payload: ActionPayload): string {
  // This is an equality fingerprint, not a cryptographic digest.
  return canonicalize(payload);
}

export function eventFingerprint(event: unknown): string {
  return canonicalize(event);
}

export function validateActionPayload(
  actionType: ActionType,
  payload: ActionPayload,
): void {
  const keys = Object.keys(payload).sort();
  if (actionType === "REPLY") {
    invariant(
      keys.length === 1 && keys[0] === "message",
      "INVALID_ACTION_PAYLOAD",
      "A reply payload must contain only message.",
    );
    invariant(
      typeof payload.message === "string" && payload.message.trim().length > 0,
      "INVALID_ACTION_PAYLOAD",
      "A reply message cannot be empty.",
    );
    invariant(
      (payload.message as string).length <= 4_000,
      "INVALID_ACTION_PAYLOAD",
      "A reply message cannot exceed 4,000 characters.",
    );
    return;
  }

  if (actionType === "SNOOZE") {
    invariant(
      keys.length === 1 && keys[0] === "minutes",
      "INVALID_ACTION_PAYLOAD",
      "A snooze payload must contain only minutes.",
    );
    invariant(
      typeof payload.minutes === "number" &&
        Number.isInteger(payload.minutes) &&
        payload.minutes >= 1 &&
        payload.minutes <= 1_440,
      "INVALID_ACTION_PAYLOAD",
      "Snooze minutes must be an integer from 1 through 1,440.",
    );
    return;
  }

  invariant(
    keys.length === 0,
    "INVALID_ACTION_PAYLOAD",
    "Dismiss does not accept a payload.",
  );
}

export function assertActionAvailable(
  capabilities: readonly ActionCapability[],
  actionType: ActionType,
): void {
  invariant(
    capabilities.includes(actionType),
    "ACTION_NOT_AVAILABLE",
    `${actionType} is not available for the current notification.`,
  );
}

export function validateEventAdvance(
  current: { version: number; fingerprint: string } | null,
  incoming: { version: number; fingerprint: string },
): "INSERT" | "IDEMPOTENT" | "UPDATE" {
  invariant(
    Number.isInteger(incoming.version) && incoming.version >= 1,
    "INVALID_EVENT_VERSION",
    "Event version must be a positive integer.",
  );
  if (current === null) {
    return "INSERT";
  }
  invariant(
    incoming.version >= current.version,
    "EVENT_VERSION_REGRESSION",
    "An event update cannot move to an older version.",
  );
  if (incoming.version === current.version) {
    invariant(
      incoming.fingerprint === current.fingerprint,
      "EVENT_VERSION_CONFLICT",
      "The same event version cannot contain different data.",
    );
    return "IDEMPOTENT";
  }
  return "UPDATE";
}

export interface ConfirmationSnapshot {
  now: number;
  expectedRevision: number;
  proposal: {
    status: ProposalStatus;
    revision: number;
    expectedEventVersion: number;
    actionType: ActionType;
    expiresAtMs: number;
  };
  event: {
    version: number;
    status: EventStatus;
    capabilities: readonly ActionCapability[];
  };
  device: {
    status: "ONLINE" | "OFFLINE" | "REHEARSAL";
    lastSeenAtMs: number;
  };
}

export function validateConfirmation(snapshot: ConfirmationSnapshot): void {
  invariant(
    snapshot.proposal.status === "PROPOSED" || snapshot.proposal.status === "REVISED",
    "PROPOSAL_NOT_CONFIRMABLE",
    "Only a current proposed or revised action can be confirmed.",
  );
  invariant(
    snapshot.proposal.revision === snapshot.expectedRevision,
    "PROPOSAL_REVISION_CHANGED",
    "The proposal changed after it was presented.",
  );
  invariant(
    snapshot.proposal.expiresAtMs > snapshot.now,
    "PROPOSAL_EXPIRED",
    "The proposal expired before confirmation.",
  );
  invariant(
    isCurrentEvent(snapshot.event.status),
    "NOTIFICATION_GONE",
    "The notification is no longer active.",
  );
  invariant(
    snapshot.event.version === snapshot.proposal.expectedEventVersion,
    "EVENT_CHANGED",
    "The notification changed after the proposal was prepared.",
  );
  assertActionAvailable(snapshot.event.capabilities, snapshot.proposal.actionType);
  invariant(
    snapshot.device.status === "REHEARSAL" ||
      (snapshot.device.status === "ONLINE" &&
        isDeviceFresh(snapshot.device.lastSeenAtMs, snapshot.now)),
    "DEVICE_OFFLINE",
    "The target device is not currently available.",
  );
}

export function confirmationGrantId(proposalId: string, revision: number): string {
  return `grant:${proposalId}:r${revision}`;
}

export function commandIdFor(proposalId: string, revision: number): string {
  return `cmd:${proposalId}:r${revision}`;
}

export function commandIdempotencyKey(
  proposalId: string,
  revision: number,
): string {
  return `${proposalId}:confirm:r${revision}`;
}

export function receiptCommandStatus(status: ReceiptStatus): CommandStatus {
  return status;
}

export function expectedTextureCue(status: ReceiptStatus): string {
  return status === "DISPATCHED" ? "ACTION_DISPATCHED" : "ACTION_FAILED";
}

export function hasValidReceiptProof(
  command: {
    commandId: string;
    targetDeviceId: string;
    traceId: string;
    claimedAtMs?: number;
    expiresAtMs: number;
    status: CommandStatus;
  },
  receipt:
    | {
        commandId: string;
        deviceId: string;
        traceId: string;
        status: ReceiptStatus;
      }
    | null,
): boolean {
  return Boolean(
    receipt &&
      receipt.commandId === command.commandId &&
      receipt.deviceId === command.targetDeviceId &&
      receipt.traceId === command.traceId &&
      command.claimedAtMs !== undefined &&
      command.claimedAtMs <= command.expiresAtMs &&
      command.status === receipt.status,
  );
}

export function clampLimit(value: number | undefined, fallback: number, max: number): number {
  if (value === undefined) {
    return fallback;
  }
  invariant(Number.isInteger(value) && value >= 1, "INVALID_LIMIT", "Limit must be positive.");
  return Math.min(value, max);
}
