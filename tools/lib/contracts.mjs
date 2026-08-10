import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const REPOSITORY_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
export const DOMAIN_CONTRACT_PATH = path.join(
  REPOSITORY_ROOT,
  "shared/contracts/domain.ts",
);
export const DEMO_FIXTURE_PATH = path.join(
  REPOSITORY_ROOT,
  "shared/fixtures/demo-events.json",
);

export const CONTRACT_VERSION = 1;
export const EVENT_STATUSES = new Set(["ACTIVE", "UPDATED", "REMOVED"]);
export const ATTENTION_LEVELS = new Set([
  "LOW",
  "NORMAL",
  "IMPORTANT",
  "URGENT",
]);
export const ACTION_TYPES = new Set(["REPLY", "DISMISS", "SNOOZE"]);
export const ACTION_CAPABILITIES = new Set([
  ...ACTION_TYPES,
  "MARK_READ",
  "OPEN_APP",
]);
export const PROPOSAL_STATUSES = new Set([
  "PROPOSED",
  "REVISED",
  "CONFIRMED",
  "COMMITTED",
  "CANCELLED",
  "EXPIRED",
  "STALE",
]);
export const COMMAND_STATUSES = new Set([
  "QUEUED",
  "CLAIMED",
  "EXECUTING",
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE",
]);
export const RECEIPT_STATUSES = new Set([
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE",
]);
export const TEXTURE_CUES = new Set([
  "LISTENING_STARTED",
  "CONTENT_MOVEMENT",
  "FOCUS_ENTERED",
  "ATTENTION_URGENT",
  "PROPOSAL_READY",
  "CONFIRMATION_REQUIRED",
  "EXECUTION_STARTED",
  "ACTION_DISPATCHED",
  "ACTION_FAILED",
  "CANCELLED",
]);
export const TEXTURE_ERROR_CODES = new Set([
  "NOTIFICATION_GONE",
  "REPLY_NOT_SUPPORTED",
  "ACTION_HANDLE_CHANGED",
  "EVENT_CHANGED",
  "PENDING_INTENT_CANCELLED",
  "COMMAND_EXPIRED",
  "UNAUTHORIZED",
  "DUPLICATE_COMMAND",
  "POLICY_BLOCKED",
  "DEVICE_OFFLINE",
  "NETWORK_ERROR",
]);

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function requireRecord(value, label) {
  invariant(isRecord(value), `${label} must be an object`);
}

function requireString(value, label, { allowEmpty = false } = {}) {
  invariant(typeof value === "string", `${label} must be a string`);
  if (!allowEmpty) {
    invariant(value.trim().length > 0, `${label} must not be empty`);
  }
}

function requireEnum(value, allowed, label) {
  requireString(value, label);
  invariant(allowed.has(value), `${label} has unsupported value: ${value}`);
}

function requireInteger(value, label, minimum = 0) {
  invariant(Number.isInteger(value), `${label} must be an integer`);
  invariant(value >= minimum, `${label} must be at least ${minimum}`);
}

function requireIsoTimestamp(value, label) {
  requireString(value, label);
  invariant(!Number.isNaN(Date.parse(value)), `${label} must be an ISO timestamp`);
}

function requirePayload(value, label) {
  requireRecord(value, label);
  for (const [key, entry] of Object.entries(value)) {
    requireString(key, `${label} key`);
    invariant(
      ["string", "number", "boolean"].includes(typeof entry),
      `${label}.${key} must be a string, number, or boolean`,
    );
  }
}

function requireContractVersion(value, label) {
  invariant(
    value === CONTRACT_VERSION,
    `${label}.contractVersion must be ${CONTRACT_VERSION}`,
  );
}

export function assertCanonicalContractVersion() {
  const source = fs.readFileSync(DOMAIN_CONTRACT_PATH, "utf8");
  const match = source.match(
    /export const CONTRACT_VERSION\s*=\s*(\d+)\s+as const/,
  );
  invariant(match, "Unable to read CONTRACT_VERSION from shared contract");
  invariant(
    Number(match[1]) === CONTRACT_VERSION,
    `Harness contract version ${CONTRACT_VERSION} does not match domain.ts ${match[1]}`,
  );
}

export function validateNotificationEvent(event, label = "NotificationEvent") {
  requireRecord(event, label);
  requireContractVersion(event.contractVersion, label);
  requireString(event.eventId, `${label}.eventId`);
  requireString(event.deviceId, `${label}.deviceId`);

  requireRecord(event.app, `${label}.app`);
  requireString(event.app.packageName, `${label}.app.packageName`);
  requireString(event.app.label, `${label}.app.label`);

  requireRecord(event.sender, `${label}.sender`);
  requireString(event.sender.displayName, `${label}.sender.displayName`);
  if (event.sender.personId !== undefined) {
    requireString(event.sender.personId, `${label}.sender.personId`);
  }
  if (event.conversationLabel !== undefined) {
    requireString(event.conversationLabel, `${label}.conversationLabel`);
  }
  if (event.body !== undefined) {
    requireString(event.body, `${label}.body`, { allowEmpty: true });
  }

  requireIsoTimestamp(event.postedAt, `${label}.postedAt`);
  requireIsoTimestamp(event.updatedAt, `${label}.updatedAt`);
  requireInteger(event.version, `${label}.version`, 1);
  requireEnum(event.status, EVENT_STATUSES, `${label}.status`);

  invariant(Array.isArray(event.capabilities), `${label}.capabilities must be an array`);
  const uniqueCapabilities = new Set(event.capabilities);
  invariant(
    uniqueCapabilities.size === event.capabilities.length,
    `${label}.capabilities must not contain duplicates`,
  );
  for (const capability of event.capabilities) {
    requireEnum(capability, ACTION_CAPABILITIES, `${label}.capabilities[]`);
  }

  requireRecord(event.priority, `${label}.priority`);
  invariant(
    typeof event.priority.score === "number" &&
      event.priority.score >= 0 &&
      event.priority.score <= 1,
    `${label}.priority.score must be between 0 and 1`,
  );
  requireEnum(
    event.priority.level,
    ATTENTION_LEVELS,
    `${label}.priority.level`,
  );
  requireString(event.priority.reason, `${label}.priority.reason`);
  return event;
}

export function validateActionProposal(
  proposal,
  label = "ActionProposal",
) {
  requireRecord(proposal, label);
  requireContractVersion(proposal.contractVersion, label);
  requireString(proposal.proposalId, `${label}.proposalId`);
  requireString(proposal.ownerId, `${label}.ownerId`);
  requireString(proposal.sessionId, `${label}.sessionId`);
  requireString(proposal.eventId, `${label}.eventId`);
  requireInteger(
    proposal.expectedEventVersion,
    `${label}.expectedEventVersion`,
    1,
  );
  requireEnum(proposal.actionType, ACTION_TYPES, `${label}.actionType`);
  requirePayload(proposal.payload, `${label}.payload`);
  requireString(proposal.spokenPreview, `${label}.spokenPreview`);
  requireEnum(proposal.status, PROPOSAL_STATUSES, `${label}.status`);
  requireIsoTimestamp(proposal.createdAt, `${label}.createdAt`);
  requireIsoTimestamp(proposal.expiresAt, `${label}.expiresAt`);
  invariant(
    Date.parse(proposal.expiresAt) > Date.parse(proposal.createdAt),
    `${label}.expiresAt must be after createdAt`,
  );
  return proposal;
}

export function validateTextureCommand(
  command,
  label = "TextureCommand",
) {
  requireRecord(command, label);
  requireContractVersion(command.contractVersion, label);
  requireString(command.commandId, `${label}.commandId`);
  requireString(command.ownerId, `${label}.ownerId`);
  requireString(command.proposalId, `${label}.proposalId`);
  requireString(command.targetDeviceId, `${label}.targetDeviceId`);
  requireString(command.eventId, `${label}.eventId`);
  requireInteger(
    command.expectedEventVersion,
    `${label}.expectedEventVersion`,
    1,
  );
  requireEnum(command.actionType, ACTION_TYPES, `${label}.actionType`);
  requirePayload(command.payload, `${label}.payload`);
  requireString(command.idempotencyKey, `${label}.idempotencyKey`);
  requireEnum(command.status, COMMAND_STATUSES, `${label}.status`);
  requireIsoTimestamp(command.createdAt, `${label}.createdAt`);
  requireIsoTimestamp(command.expiresAt, `${label}.expiresAt`);
  invariant(
    Date.parse(command.expiresAt) > Date.parse(command.createdAt),
    `${label}.expiresAt must be after createdAt`,
  );
  return command;
}

export function validateActionReceipt(receipt, label = "ActionReceipt") {
  requireRecord(receipt, label);
  requireContractVersion(receipt.contractVersion, label);
  requireString(receipt.receiptId, `${label}.receiptId`);
  requireString(receipt.commandId, `${label}.commandId`);
  requireString(receipt.deviceId, `${label}.deviceId`);
  requireEnum(receipt.status, RECEIPT_STATUSES, `${label}.status`);
  if (receipt.errorCode !== undefined) {
    requireEnum(receipt.errorCode, TEXTURE_ERROR_CODES, `${label}.errorCode`);
  }
  requireString(receipt.message, `${label}.message`);
  requireIsoTimestamp(receipt.deviceTimestamp, `${label}.deviceTimestamp`);
  requireEnum(receipt.textureCue, TEXTURE_CUES, `${label}.textureCue`);
  requireString(receipt.traceId, `${label}.traceId`);
  return receipt;
}

export function validateProposalBinding(proposal, event) {
  invariant(
    proposal.eventId === event.eventId,
    "Proposal must bind to the selected event ID",
  );
  invariant(
    proposal.expectedEventVersion === event.version,
    "Proposal must bind to the selected event version",
  );
}

export function validateCommandBinding(command, proposal) {
  invariant(
    command.proposalId === proposal.proposalId,
    "Command must bind to the confirmed proposal",
  );
  invariant(command.eventId === proposal.eventId, "Command event ID changed");
  invariant(
    command.expectedEventVersion === proposal.expectedEventVersion,
    "Command event version changed",
  );
  invariant(command.actionType === proposal.actionType, "Command action changed");
  invariant(
    JSON.stringify(command.payload) === JSON.stringify(proposal.payload),
    "Command payload changed after confirmation",
  );
}

export function loadDemoFixtures() {
  assertCanonicalContractVersion();
  const fixtures = JSON.parse(fs.readFileSync(DEMO_FIXTURE_PATH, "utf8"));
  invariant(Array.isArray(fixtures), "Demo fixture file must contain an array");
  const ids = new Set();
  for (const fixture of fixtures) {
    validateNotificationEvent(fixture);
    invariant(!ids.has(fixture.eventId), `Duplicate fixture eventId: ${fixture.eventId}`);
    ids.add(fixture.eventId);
  }
  return structuredClone(fixtures);
}

export function findFixture(fixtures, eventId) {
  const fixture = fixtures.find((candidate) => candidate.eventId === eventId);
  invariant(fixture, `Unknown fixture eventId: ${eventId}`);
  return structuredClone(fixture);
}

export function assertNoReceipt(value, pathLabel = "output") {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => assertNoReceipt(entry, `${pathLabel}[${index}]`));
    return;
  }
  if (!isRecord(value)) {
    return;
  }
  invariant(
    !("receiptId" in value),
    `${pathLabel} contains a receipt; rehearsal tools may not create receipts`,
  );
  for (const [key, entry] of Object.entries(value)) {
    assertNoReceipt(entry, `${pathLabel}.${key}`);
  }
}

