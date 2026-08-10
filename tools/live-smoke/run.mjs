#!/usr/bin/env node

import { randomUUID } from "node:crypto";
import { ConvexHttpClient } from "convex/browser";
import { makeFunctionReference } from "convex/server";

const CONTRACT_VERSION = 1;
const DEFAULT_STEP_TIMEOUT_MS = 10_000;
const DEFAULT_RUN_TIMEOUT_MS = 60_000;
const startedAtMs = Date.now();

class ContractMismatchError extends Error {
  constructor(path, expectation) {
    super(`Contract mismatch at ${path}: ${expectation}`);
    this.name = "ContractMismatchError";
  }
}

function requiredEnvironment(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`Missing required environment variable ${name}.`);
  }
  return value;
}

function boundedIntegerEnvironment(name, fallback, minimum, maximum) {
  const raw = process.env[name];
  if (raw === undefined || raw.trim() === "") {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} through ${maximum}.`);
  }
  return value;
}

function safeIdentifierEnvironment(name, fallback) {
  const value = process.env[name]?.trim() || fallback;
  if (!/^[a-zA-Z0-9._:-]{3,128}$/.test(value)) {
    throw new Error(`${name} must be a 3-128 character identifier.`);
  }
  return value;
}

function requireObject(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new ContractMismatchError(path, "expected an object");
  }
  return value;
}

function requireArray(value, path) {
  if (!Array.isArray(value)) {
    throw new ContractMismatchError(path, "expected an array");
  }
  return value;
}

function requireString(value, path) {
  if (typeof value !== "string" || value.length === 0) {
    throw new ContractMismatchError(path, "expected a non-empty string");
  }
  return value;
}

function requireOptionalString(value, path) {
  if (value !== undefined) {
    requireString(value, path);
  }
}

function requireNumber(value, path) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ContractMismatchError(path, "expected a finite number");
  }
  return value;
}

function requirePositiveInteger(value, path) {
  requireNumber(value, path);
  if (!Number.isInteger(value) || value < 1) {
    throw new ContractMismatchError(path, "expected a positive integer");
  }
  return value;
}

function requireBoolean(value, path) {
  if (typeof value !== "boolean") {
    throw new ContractMismatchError(path, "expected a boolean");
  }
  return value;
}

function requireLiteral(value, expected, path) {
  if (value !== expected) {
    throw new ContractMismatchError(path, `expected ${JSON.stringify(expected)}`);
  }
  return value;
}

function requireEnum(value, allowed, path) {
  if (!allowed.includes(value)) {
    throw new ContractMismatchError(path, `expected one of ${allowed.join(", ")}`);
  }
  return value;
}

function requireIsoTimestamp(value, path) {
  requireString(value, path);
  if (!Number.isFinite(Date.parse(value))) {
    throw new ContractMismatchError(path, "expected an ISO-8601 timestamp");
  }
  return value;
}

function validatePrimitiveRecord(value, path) {
  const record = requireObject(value, path);
  for (const [key, item] of Object.entries(record)) {
    if (!["string", "number", "boolean"].includes(typeof item)) {
      throw new ContractMismatchError(`${path}.${key}`, "expected a string, number, or boolean");
    }
    if (typeof item === "number" && !Number.isFinite(item)) {
      throw new ContractMismatchError(`${path}.${key}`, "expected a finite number");
    }
  }
  return record;
}

function validateNotificationEvent(value, path = "event") {
  const event = requireObject(value, path);
  requireLiteral(event.contractVersion, CONTRACT_VERSION, `${path}.contractVersion`);
  requireString(event.eventId, `${path}.eventId`);
  requireString(event.deviceId, `${path}.deviceId`);
  const app = requireObject(event.app, `${path}.app`);
  requireString(app.packageName, `${path}.app.packageName`);
  requireString(app.label, `${path}.app.label`);
  const sender = requireObject(event.sender, `${path}.sender`);
  requireString(sender.displayName, `${path}.sender.displayName`);
  requireOptionalString(sender.personId, `${path}.sender.personId`);
  requireOptionalString(event.conversationLabel, `${path}.conversationLabel`);
  requireOptionalString(event.body, `${path}.body`);
  requireIsoTimestamp(event.postedAt, `${path}.postedAt`);
  requireIsoTimestamp(event.updatedAt, `${path}.updatedAt`);
  requirePositiveInteger(event.version, `${path}.version`);
  requireEnum(event.status, ["ACTIVE", "UPDATED", "REMOVED"], `${path}.status`);
  const capabilities = requireArray(event.capabilities, `${path}.capabilities`);
  for (const [index, capability] of capabilities.entries()) {
    requireEnum(
      capability,
      ["REPLY", "DISMISS", "SNOOZE", "MARK_READ", "OPEN_APP"],
      `${path}.capabilities[${index}]`,
    );
  }
  const priority = requireObject(event.priority, `${path}.priority`);
  const score = requireNumber(priority.score, `${path}.priority.score`);
  if (score < 0 || score > 1) {
    throw new ContractMismatchError(`${path}.priority.score`, "expected a value from 0 through 1");
  }
  requireEnum(
    priority.level,
    ["LOW", "NORMAL", "IMPORTANT", "URGENT"],
    `${path}.priority.level`,
  );
  requireString(priority.reason, `${path}.priority.reason`);
  return event;
}

function validateProposal(value, path = "proposal") {
  const proposal = requireObject(value, path);
  requireLiteral(proposal.contractVersion, CONTRACT_VERSION, `${path}.contractVersion`);
  requireString(proposal.proposalId, `${path}.proposalId`);
  requireString(proposal.ownerId, `${path}.ownerId`);
  requireString(proposal.sessionId, `${path}.sessionId`);
  requireString(proposal.eventId, `${path}.eventId`);
  requirePositiveInteger(proposal.expectedEventVersion, `${path}.expectedEventVersion`);
  requireEnum(proposal.actionType, ["REPLY", "DISMISS", "SNOOZE"], `${path}.actionType`);
  validatePrimitiveRecord(proposal.payload, `${path}.payload`);
  requireString(proposal.spokenPreview, `${path}.spokenPreview`);
  requireEnum(
    proposal.status,
    ["PROPOSED", "REVISED", "CONFIRMED", "COMMITTED", "CANCELLED", "EXPIRED", "STALE"],
    `${path}.status`,
  );
  requireIsoTimestamp(proposal.createdAt, `${path}.createdAt`);
  requireIsoTimestamp(proposal.expiresAt, `${path}.expiresAt`);
  requirePositiveInteger(proposal.revision, `${path}.revision`);
  return proposal;
}

function validateCommand(value, path = "command") {
  const command = requireObject(value, path);
  requireLiteral(command.contractVersion, CONTRACT_VERSION, `${path}.contractVersion`);
  requireString(command.commandId, `${path}.commandId`);
  requireString(command.ownerId, `${path}.ownerId`);
  requireString(command.proposalId, `${path}.proposalId`);
  requireString(command.targetDeviceId, `${path}.targetDeviceId`);
  requireString(command.eventId, `${path}.eventId`);
  requirePositiveInteger(command.expectedEventVersion, `${path}.expectedEventVersion`);
  requireEnum(command.actionType, ["REPLY", "DISMISS", "SNOOZE"], `${path}.actionType`);
  validatePrimitiveRecord(command.payload, `${path}.payload`);
  requireString(command.idempotencyKey, `${path}.idempotencyKey`);
  requireEnum(
    command.status,
    ["QUEUED", "CLAIMED", "EXECUTING", "DISPATCHED", "FAILED", "EXPIRED", "STALE"],
    `${path}.status`,
  );
  requireIsoTimestamp(command.createdAt, `${path}.createdAt`);
  requireIsoTimestamp(command.expiresAt, `${path}.expiresAt`);
  requireString(command.traceId, `${path}.traceId`);
  return command;
}

function validateReceipt(value, path = "receipt") {
  const receipt = requireObject(value, path);
  requireLiteral(receipt.contractVersion, CONTRACT_VERSION, `${path}.contractVersion`);
  requireString(receipt.receiptId, `${path}.receiptId`);
  requireString(receipt.commandId, `${path}.commandId`);
  requireString(receipt.deviceId, `${path}.deviceId`);
  requireEnum(receipt.status, ["DISPATCHED", "FAILED", "EXPIRED", "STALE"], `${path}.status`);
  if (receipt.errorCode !== undefined) {
    requireEnum(
      receipt.errorCode,
      [
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
      ],
      `${path}.errorCode`,
    );
  }
  requireString(receipt.message, `${path}.message`);
  requireIsoTimestamp(receipt.deviceTimestamp, `${path}.deviceTimestamp`);
  requireEnum(
    receipt.textureCue,
    [
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
    ],
    `${path}.textureCue`,
  );
  requireString(receipt.traceId, `${path}.traceId`);
  return receipt;
}

function validateDevice(value, expectedDeviceId, path = "device") {
  const device = requireObject(value, path);
  requireLiteral(device.contractVersion, CONTRACT_VERSION, `${path}.contractVersion`);
  requireLiteral(device.deviceId, expectedDeviceId, `${path}.deviceId`);
  requireLiteral(device.platform, "SIMULATOR", `${path}.platform`);
  requireLiteral(device.status, "REHEARSAL", `${path}.status`);
  requireString(device.displayName, `${path}.displayName`);
  requireString(device.appVersion, `${path}.appVersion`);
  requireIsoTimestamp(device.lastSeenAt, `${path}.lastSeenAt`);
  return device;
}

function assertField(value, expected, path) {
  requireLiteral(value, expected, path);
}

function formatError(error, secretValues) {
  const chain = [];
  let current = error;
  while (current instanceof Error) {
    chain.push(current.stack || `${current.name}: ${current.message}`);
    current = current.cause;
  }
  let output = chain.join("\nCaused by:\n") || String(error);
  for (const secret of [...secretValues].sort((left, right) => right.length - left.length)) {
    output = output.split(secret).join("[REDACTED]");
  }
  return output
    .replace(/(authorization|api[_-]?key|token)(\s*[:=]\s*)[^\s,}\]]+/gi, "$1$2[REDACTED]")
    .replace(/eyJ[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+){0,2}/g, "[REDACTED]");
}

async function main() {
  const convexUrl = requiredEnvironment("CONVEX_URL");
  const bridgeToken = requiredEnvironment("TEXTUREFLOW_BRIDGE_TOKEN");
  const deviceToken = requiredEnvironment("TEXTUREFLOW_DEVICE_TOKEN");
  const userToken = requiredEnvironment("TEXTUREFLOW_USER_TOKEN");
  const secrets = new Set([bridgeToken, deviceToken, userToken]);
  if (secrets.size !== 3) {
    throw new Error("The bridge, device, and user role tokens must be distinct.");
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(convexUrl);
  } catch {
    throw new Error("CONVEX_URL must be a valid URL.");
  }
  if (!["http:", "https:"].includes(parsedUrl.protocol)) {
    throw new Error("CONVEX_URL must use HTTP or HTTPS.");
  }
  if (parsedUrl.username || parsedUrl.password || parsedUrl.search || parsedUrl.hash) {
    throw new Error("CONVEX_URL must not contain credentials, query parameters, or a fragment.");
  }

  const ownerId = safeIdentifierEnvironment("TEXTUREFLOW_SMOKE_OWNER_ID", "textureflow-live-smoke");
  const deviceId = safeIdentifierEnvironment(
    "TEXTUREFLOW_SMOKE_DEVICE_ID",
    "textureflow-smoke-simulator-v1",
  );
  const stepTimeoutMs = boundedIntegerEnvironment(
    "TEXTUREFLOW_SMOKE_STEP_TIMEOUT_MS",
    DEFAULT_STEP_TIMEOUT_MS,
    1_000,
    20_000,
  );
  const runTimeoutMs = boundedIntegerEnvironment(
    "TEXTUREFLOW_SMOKE_RUN_TIMEOUT_MS",
    DEFAULT_RUN_TIMEOUT_MS,
    10_000,
    120_000,
  );

  const client = new ConvexHttpClient(parsedUrl.origin);
  const mutation = (name, args) =>
    client.mutation(makeFunctionReference(name), args);
  const query = (name, args) =>
    client.query(makeFunctionReference(name), args);
  const bridgeActor = { ownerId, role: "BRIDGE", token: bridgeToken };
  const deviceActor = { ownerId, role: "DEVICE", deviceId, token: deviceToken };
  const userActor = { ownerId, role: "USER", token: userToken };

  async function step(name, operation, validate) {
    const elapsedMs = Date.now() - startedAtMs;
    const remainingMs = runTimeoutMs - elapsedMs;
    if (remainingMs <= 0) {
      throw new Error(`${name} was not started because the ${runTimeoutMs}ms run limit expired.`);
    }
    const timeoutMs = Math.min(stepTimeoutMs, remainingMs);
    let timer;
    try {
      const result = await Promise.race([
        operation(),
        new Promise((_, reject) => {
          timer = setTimeout(
            () => reject(new Error(`${name} exceeded its ${timeoutMs}ms timeout.`)),
            timeoutMs,
          );
        }),
      ]);
      validate(result);
      console.log(`[ok] ${name}`);
      return result;
    } catch (error) {
      throw new Error(`${name} failed`, { cause: error });
    } finally {
      clearTimeout(timer);
    }
  }

  const runId = randomUUID();
  const traceId = `smoke-trace-${runId}`;
  const eventId = `smoke-event-${runId}`;
  const proposalId = `smoke-proposal-${runId}`;
  const sessionId = `smoke-session-${runId}`;
  const claimToken = `smoke-claim-${randomUUID()}`;
  const receiptId = `smoke-receipt-${runId}`;
  let eventWasStored = false;
  let primaryError;

  console.log(`[info] TextureFlow live smoke started against ${parsedUrl.hostname}`);
  console.log("[info] Safety mode: synthetic fixture, rehearsal simulator, no Android action invocation");

  try {
    await step(
      "devices:register",
      () =>
        mutation("devices:register", {
          actor: deviceActor,
          deviceId,
          displayName: "TextureFlow Live Smoke Simulator",
          platform: "SIMULATOR",
          status: "REHEARSAL",
          appVersion: "live-smoke/1",
        }),
      (result) => validateDevice(result, deviceId),
    );

    await step(
      "devices:heartbeat",
      () =>
        mutation("devices:heartbeat", {
          actor: deviceActor,
          appVersion: "live-smoke/1",
          deviceTimestamp: new Date().toISOString(),
          traceId,
        }),
      (result) => {
        const heartbeat = requireObject(result, "heartbeat");
        assertField(heartbeat.deviceId, deviceId, "heartbeat.deviceId");
        requireIsoTimestamp(heartbeat.acceptedAt, "heartbeat.acceptedAt");
      },
    );

    const eventTimestamp = new Date().toISOString();
    const event = {
      contractVersion: CONTRACT_VERSION,
      eventId,
      deviceId,
      app: {
        packageName: "app.textureflow.fixture.smoke",
        label: "TextureFlow Smoke Fixture",
      },
      sender: {
        displayName: "Smoke Test Sender",
        personId: "fixture:smoke-test-sender",
      },
      conversationLabel: "Synthetic verification thread",
      body: "Synthetic notification used only for backend contract verification.",
      postedAt: eventTimestamp,
      updatedAt: eventTimestamp,
      version: 1,
      status: "ACTIVE",
      capabilities: ["REPLY", "DISMISS", "SNOOZE"],
      priority: {
        score: 0.8,
        level: "IMPORTANT",
        reason: "Synthetic live-smoke fixture",
      },
    };

    await step(
      "events:upsert",
      () => mutation("events:upsert", { actor: deviceActor, event, traceId }),
      (result) => {
        const response = requireObject(result, "eventUpsert");
        requireEnum(response.operation, ["INSERT", "IDEMPOTENT", "UPDATE"], "eventUpsert.operation");
        const storedEvent = validateNotificationEvent(response.event, "eventUpsert.event");
        assertField(storedEvent.eventId, eventId, "eventUpsert.event.eventId");
        assertField(storedEvent.deviceId, deviceId, "eventUpsert.event.deviceId");
        assertField(storedEvent.version, 1, "eventUpsert.event.version");
        assertField(storedEvent.status, "ACTIVE", "eventUpsert.event.status");
        const stale = requireObject(response.stale, "eventUpsert.stale");
        requireNumber(stale.proposals, "eventUpsert.stale.proposals");
        requireNumber(stale.commands, "eventUpsert.stale.commands");
      },
    );
    eventWasStored = true;

    await step(
      "attention:list",
      () => query("attention:list", { actor: userActor, limit: 20 }),
      (result) => {
        const attention = requireArray(result, "attention");
        const fixture = attention.find((item) => item?.eventId === eventId);
        if (!fixture) {
          throw new ContractMismatchError("attention", "expected the inserted fixture event");
        }
        validateNotificationEvent(fixture, "attention.fixture");
        requireBoolean(fixture.deviceOnline, "attention.fixture.deviceOnline");
        assertField(fixture.deviceOnline, true, "attention.fixture.deviceOnline");
      },
    );

    const created = await step(
      "proposals:create",
      () =>
        mutation("proposals:create", {
          actor: bridgeActor,
          proposalId,
          sessionId,
          eventId,
          actionType: "REPLY",
          payload: { message: "Synthetic smoke reply draft." },
          spokenPreview: "Reply to the synthetic fixture with the smoke draft.",
          expiresAt: new Date(Date.now() + 90_000).toISOString(),
          traceId,
        }),
      (result) => {
        const response = requireObject(result, "proposalCreate");
        requireEnum(response.operation, ["INSERT", "IDEMPOTENT"], "proposalCreate.operation");
        const proposal = validateProposal(response.proposal, "proposalCreate.proposal");
        assertField(proposal.proposalId, proposalId, "proposalCreate.proposal.proposalId");
        assertField(proposal.eventId, eventId, "proposalCreate.proposal.eventId");
        assertField(proposal.status, "PROPOSED", "proposalCreate.proposal.status");
        assertField(proposal.revision, 1, "proposalCreate.proposal.revision");
      },
    );
    const createdProposal = requireObject(created, "proposalCreate").proposal;

    const revised = await step(
      "proposals:revise",
      () =>
        mutation("proposals:revise", {
          actor: bridgeActor,
          proposalId,
          expectedRevision: createdProposal.revision,
          payload: { message: "Synthetic smoke reply, revised." },
          spokenPreview: "Reply to the synthetic fixture with the revised smoke draft.",
          expiresAt: new Date(Date.now() + 90_000).toISOString(),
        }),
      (result) => {
        const response = requireObject(result, "proposalRevise");
        assertField(response.ok, true, "proposalRevise.ok");
        const proposal = validateProposal(response.proposal, "proposalRevise.proposal");
        assertField(proposal.proposalId, proposalId, "proposalRevise.proposal.proposalId");
        assertField(proposal.status, "REVISED", "proposalRevise.proposal.status");
        assertField(proposal.revision, 2, "proposalRevise.proposal.revision");
        assertField(
          proposal.payload.message,
          "Synthetic smoke reply, revised.",
          "proposalRevise.proposal.payload.message",
        );
      },
    );
    const revisedProposal = requireObject(revised, "proposalRevise").proposal;

    const confirmed = await step(
      "proposals:confirm",
      () =>
        mutation("proposals:confirm", {
          actor: bridgeActor,
          proposalId,
          sessionId,
          expectedRevision: revisedProposal.revision,
        }),
      (result) => {
        const response = requireObject(result, "proposalConfirm");
        assertField(response.ok, true, "proposalConfirm.ok");
        assertField(response.duplicate, false, "proposalConfirm.duplicate");
        const proposal = validateProposal(response.proposal, "proposalConfirm.proposal");
        const command = validateCommand(response.command, "proposalConfirm.command");
        assertField(proposal.status, "COMMITTED", "proposalConfirm.proposal.status");
        assertField(command.proposalId, proposalId, "proposalConfirm.command.proposalId");
        assertField(command.eventId, eventId, "proposalConfirm.command.eventId");
        assertField(command.targetDeviceId, deviceId, "proposalConfirm.command.targetDeviceId");
        assertField(command.status, "QUEUED", "proposalConfirm.command.status");
        assertField(command.traceId, traceId, "proposalConfirm.command.traceId");
        if (response.receipt !== null) {
          throw new ContractMismatchError("proposalConfirm.receipt", "expected null before execution");
        }
      },
    );
    const command = requireObject(confirmed, "proposalConfirm").command;
    const commandId = command.commandId;

    await step(
      "commands:claim",
      () => mutation("commands:claim", { actor: deviceActor, commandId, claimToken }),
      (result) => {
        const response = requireObject(result, "commandClaim");
        assertField(response.claimed, true, "commandClaim.claimed");
        assertField(response.duplicate, false, "commandClaim.duplicate");
        const claimedCommand = validateCommand(response.command, "commandClaim.command");
        assertField(claimedCommand.commandId, commandId, "commandClaim.command.commandId");
        assertField(claimedCommand.status, "CLAIMED", "commandClaim.command.status");
        if (claimedCommand.claimToken !== claimToken) {
          throw new ContractMismatchError("commandClaim.command.claimToken", "expected the submitted claim");
        }
      },
    );

    await step(
      "commands:startExecution",
      () => mutation("commands:startExecution", { actor: deviceActor, commandId, claimToken }),
      (result) => {
        const response = requireObject(result, "commandStart");
        assertField(response.operation, "UPDATE", "commandStart.operation");
        const executingCommand = validateCommand(response.command, "commandStart.command");
        assertField(executingCommand.commandId, commandId, "commandStart.command.commandId");
        assertField(executingCommand.status, "EXECUTING", "commandStart.command.status");
      },
    );

    const receiptInput = {
      contractVersion: CONTRACT_VERSION,
      receiptId,
      commandId,
      deviceId,
      status: "FAILED",
      errorCode: "POLICY_BLOCKED",
      message: "Live smoke harness intentionally stopped before Android action execution.",
      deviceTimestamp: new Date().toISOString(),
      textureCue: "ACTION_FAILED",
      traceId,
    };

    await step(
      "receipts:complete",
      () => mutation("receipts:complete", { actor: deviceActor, claimToken, receipt: receiptInput }),
      (result) => {
        const response = requireObject(result, "receiptComplete");
        assertField(response.operation, "INSERT", "receiptComplete.operation");
        const receipt = validateReceipt(response.receipt, "receiptComplete.receipt");
        assertField(receipt.receiptId, receiptId, "receiptComplete.receipt.receiptId");
        assertField(receipt.commandId, commandId, "receiptComplete.receipt.commandId");
        assertField(receipt.status, "FAILED", "receiptComplete.receipt.status");
        assertField(receipt.errorCode, "POLICY_BLOCKED", "receiptComplete.receipt.errorCode");
        assertField(receipt.textureCue, "ACTION_FAILED", "receiptComplete.receipt.textureCue");
      },
    );

    await step(
      "receipts:getByCommand",
      () => query("receipts:getByCommand", { actor: userActor, commandId }),
      (result) => {
        const receipt = validateReceipt(result, "receiptQuery");
        assertField(receipt.receiptId, receiptId, "receiptQuery.receiptId");
        assertField(receipt.commandId, commandId, "receiptQuery.commandId");
        assertField(receipt.status, "FAILED", "receiptQuery.status");
        assertField(receipt.errorCode, "POLICY_BLOCKED", "receiptQuery.errorCode");
      },
    );
  } catch (error) {
    primaryError = error;
  } finally {
    if (eventWasStored) {
      try {
        await step(
          "events:markRemoved (fixture cleanup)",
          () =>
            mutation("events:markRemoved", {
              actor: deviceActor,
              eventId,
              version: 2,
              updatedAt: new Date().toISOString(),
              traceId,
            }),
          (result) => {
            const response = requireObject(result, "eventCleanup");
            requireEnum(response.operation, ["UPDATE", "IDEMPOTENT"], "eventCleanup.operation");
            const removed = validateNotificationEvent(response.event, "eventCleanup.event");
            assertField(removed.eventId, eventId, "eventCleanup.event.eventId");
            assertField(removed.version, 2, "eventCleanup.event.version");
            assertField(removed.status, "REMOVED", "eventCleanup.event.status");
          },
        );
      } catch (cleanupError) {
        if (primaryError) {
          console.error(`[warn] Fixture cleanup also failed:\n${formatError(cleanupError, secrets)}`);
        } else {
          primaryError = cleanupError;
        }
      }
    }
  }

  if (primaryError) {
    throw Object.assign(primaryError, { secretValues: secrets });
  }

  console.log(`[pass] Backend lifecycle verified in ${Date.now() - startedAtMs}ms`);
  console.log("[pass] No Android PendingIntent was invoked; execution ended with POLICY_BLOCKED");
}

main().catch((error) => {
  const secretValues = error?.secretValues || new Set(
    [
      process.env.TEXTUREFLOW_BRIDGE_TOKEN,
      process.env.TEXTUREFLOW_DEVICE_TOKEN,
      process.env.TEXTUREFLOW_USER_TOKEN,
    ].filter(Boolean),
  );
  console.error(`[fail] TextureFlow live smoke failed:\n${formatError(error, secretValues)}`);
  process.exitCode = 1;
});
