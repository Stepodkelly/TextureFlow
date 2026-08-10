import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

import {
  actionCapabilityValidator,
  actionTypeValidator,
  appValidator,
  attentionLevelValidator,
  commandStatusValidator,
  deviceStatusValidator,
  eventStatusValidator,
  payloadValidator,
  platformValidator,
  priorityValidator,
  proposalStatusValidator,
  receiptStatusValidator,
  senderValidator,
  sourceModeValidator,
  textureCueValidator,
  textureErrorCodeValidator,
  traceAttributesValidator,
  traceCorrelationValidator,
  traceModeValidator,
  traceOutcomeValidator,
  traceServiceValidator,
} from "./lib/validators";

export default defineSchema({
  users: defineTable({
    ownerId: v.string(),
    primarySubject: v.string(),
    displayName: v.optional(v.string()),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
  }).index("by_owner", ["ownerId"]),

  devices: defineTable({
    ownerId: v.string(),
    deviceId: v.string(),
    authSubject: v.string(),
    displayName: v.string(),
    platform: platformValidator,
    status: deviceStatusValidator,
    appVersion: v.string(),
    contractVersion: v.literal(1),
    lastSeenAt: v.string(),
    lastSeenAtMs: v.number(),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
  })
    .index("by_owner_device", ["ownerId", "deviceId"])
    .index("by_auth_subject", ["authSubject"])
    .index("by_owner_last_seen", ["ownerId", "lastSeenAtMs"]),

  persons: defineTable({
    ownerId: v.string(),
    personId: v.string(),
    displayName: v.string(),
    relationshipLabel: v.optional(v.string()),
    importance: v.number(),
    provisional: v.boolean(),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
  })
    .index("by_owner_person", ["ownerId", "personId"])
    .index("by_owner_display_name", ["ownerId", "displayName"]),

  identities: defineTable({
    ownerId: v.string(),
    identityId: v.string(),
    personId: v.string(),
    packageName: v.string(),
    handle: v.string(),
    normalizedHandle: v.string(),
    verified: v.boolean(),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
  })
    .index("by_owner_identity", ["ownerId", "identityId"])
    .index("by_owner_person", ["ownerId", "personId"])
    .index("by_owner_package_handle", ["ownerId", "packageName", "normalizedHandle"]),

  notificationEvents: defineTable({
    ownerId: v.string(),
    contractVersion: v.literal(1),
    eventId: v.string(),
    deviceId: v.string(),
    app: appValidator,
    sender: senderValidator,
    conversationLabel: v.optional(v.string()),
    body: v.optional(v.string()),
    postedAt: v.string(),
    postedAtMs: v.number(),
    updatedAt: v.string(),
    updatedAtMs: v.number(),
    version: v.number(),
    status: eventStatusValidator,
    capabilities: v.array(actionCapabilityValidator),
    priority: priorityValidator,
    sourceMode: sourceModeValidator,
    fingerprint: v.string(),
    syncedAtMs: v.number(),
  })
    .index("by_owner_event", ["ownerId", "eventId"])
    .index("by_owner_device", ["ownerId", "deviceId"])
    .index("by_owner_status_priority", ["ownerId", "status", "priority.score"])
    .index("by_owner_person_updated", ["ownerId", "sender.personId", "updatedAtMs"])
    .index("by_owner_updated", ["ownerId", "updatedAtMs"]),

  attentionAssessments: defineTable({
    ownerId: v.string(),
    eventId: v.string(),
    eventVersion: v.number(),
    score: v.number(),
    level: attentionLevelValidator,
    reason: v.string(),
    source: v.union(v.literal("DEVICE"), v.literal("DETERMINISTIC"), v.literal("MODEL")),
    assessedAtMs: v.number(),
  })
    .index("by_owner_event", ["ownerId", "eventId"])
    .index("by_owner_level_score", ["ownerId", "level", "score"]),

  voiceSessions: defineTable({
    ownerId: v.string(),
    sessionId: v.string(),
    status: v.string(),
    activeProposalId: v.optional(v.string()),
    startedAtMs: v.number(),
    updatedAtMs: v.number(),
    expiresAtMs: v.number(),
  }).index("by_owner_session", ["ownerId", "sessionId"]),

  actionProposals: defineTable({
    ownerId: v.string(),
    contractVersion: v.literal(1),
    proposalId: v.string(),
    sessionId: v.string(),
    eventId: v.string(),
    expectedEventVersion: v.number(),
    targetDeviceId: v.string(),
    actionType: actionTypeValidator,
    payload: payloadValidator,
    payloadFingerprint: v.string(),
    spokenPreview: v.string(),
    revision: v.number(),
    status: proposalStatusValidator,
    sourceMode: sourceModeValidator,
    traceId: v.string(),
    createdAt: v.string(),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
    expiresAt: v.string(),
    expiresAtMs: v.number(),
    staleReason: v.optional(v.string()),
    confirmedAtMs: v.optional(v.number()),
  })
    .index("by_owner_proposal", ["ownerId", "proposalId"])
    .index("by_owner_event", ["ownerId", "eventId"])
    .index("by_owner_session", ["ownerId", "sessionId"]),

  confirmationGrants: defineTable({
    ownerId: v.string(),
    grantId: v.string(),
    proposalId: v.string(),
    proposalRevision: v.number(),
    sessionId: v.string(),
    payloadFingerprint: v.string(),
    confirmerSubject: v.string(),
    confirmedAt: v.string(),
    confirmedAtMs: v.number(),
    expiresAtMs: v.number(),
  })
    .index("by_owner_grant", ["ownerId", "grantId"])
    .index("by_owner_proposal", ["ownerId", "proposalId"]),

  commands: defineTable({
    ownerId: v.string(),
    contractVersion: v.literal(1),
    commandId: v.string(),
    proposalId: v.string(),
    proposalRevision: v.number(),
    confirmationGrantId: v.string(),
    targetDeviceId: v.string(),
    eventId: v.string(),
    expectedEventVersion: v.number(),
    actionType: actionTypeValidator,
    payload: payloadValidator,
    idempotencyKey: v.string(),
    status: commandStatusValidator,
    sourceMode: sourceModeValidator,
    traceId: v.string(),
    claimToken: v.optional(v.string()),
    claimedAtMs: v.optional(v.number()),
    executingAtMs: v.optional(v.number()),
    finalAtMs: v.optional(v.number()),
    staleReason: v.optional(v.string()),
    createdAt: v.string(),
    createdAtMs: v.number(),
    expiresAt: v.string(),
    expiresAtMs: v.number(),
  })
    .index("by_owner_command", ["ownerId", "commandId"])
    .index("by_owner_proposal", ["ownerId", "proposalId"])
    .index("by_owner_device_status", ["ownerId", "targetDeviceId", "status"])
    .index("by_owner_idempotency", ["ownerId", "idempotencyKey"]),

  actionReceipts: defineTable({
    ownerId: v.string(),
    contractVersion: v.literal(1),
    receiptId: v.string(),
    commandId: v.string(),
    deviceId: v.string(),
    status: receiptStatusValidator,
    errorCode: v.optional(textureErrorCodeValidator),
    message: v.string(),
    deviceTimestamp: v.string(),
    deviceTimestampMs: v.number(),
    textureCue: textureCueValidator,
    traceId: v.string(),
    receivedAtMs: v.number(),
  })
    .index("by_owner_receipt", ["ownerId", "receiptId"])
    .index("by_owner_command", ["ownerId", "commandId"])
    .index("by_owner_device", ["ownerId", "deviceId"]),

  texturePreferences: defineTable({
    ownerId: v.string(),
    profile: v.string(),
    audioEnabled: v.boolean(),
    hapticsEnabled: v.boolean(),
    visualsEnabled: v.boolean(),
    intensity: v.number(),
    updatedAtMs: v.number(),
  }).index("by_owner", ["ownerId"]),

  traceEvents: defineTable({
    ownerId: v.string(),
    contractVersion: v.literal(1),
    mode: traceModeValidator,
    traceId: v.string(),
    spanId: v.string(),
    parentSpanId: v.optional(v.string()),
    sequence: v.optional(v.number()),
    name: v.string(),
    service: traceServiceValidator,
    outcome: traceOutcomeValidator,
    occurredAt: v.string(),
    occurredAtMs: v.number(),
    durationMs: v.optional(v.number()),
    correlation: traceCorrelationValidator,
    attributes: traceAttributesValidator,
  })
    .index("by_owner_trace", ["ownerId", "traceId"])
    .index("by_owner_occurred", ["ownerId", "occurredAtMs"])
    .index("by_owner_event", ["ownerId", "correlation.eventId", "occurredAtMs"])
    .index("by_owner_proposal", ["ownerId", "correlation.proposalId", "occurredAtMs"])
    .index("by_owner_command", ["ownerId", "correlation.commandId", "occurredAtMs"])
    .index("by_owner_device", ["ownerId", "correlation.deviceId", "occurredAtMs"]),

  demoFixtures: defineTable({
    ownerId: v.string(),
    fixtureId: v.string(),
    label: v.string(),
    eventTemplate: v.object({
      contractVersion: v.literal(1),
      eventId: v.string(),
      deviceId: v.string(),
      app: appValidator,
      sender: senderValidator,
      conversationLabel: v.optional(v.string()),
      body: v.optional(v.string()),
      postedAt: v.string(),
      updatedAt: v.string(),
      version: v.number(),
      status: eventStatusValidator,
      capabilities: v.array(actionCapabilityValidator),
      priority: priorityValidator,
    }),
    createdAtMs: v.number(),
    updatedAtMs: v.number(),
  }).index("by_owner_fixture", ["ownerId", "fixtureId"]),
});
