import { v } from "convex/values";

export const actorRoleValidator = v.union(
  v.literal("USER"),
  v.literal("BRIDGE"),
  v.literal("DEVICE"),
);

export const actorInputValidator = v.object({
  ownerId: v.string(),
  role: actorRoleValidator,
  deviceId: v.optional(v.string()),
  token: v.optional(v.string()),
});

export const eventStatusValidator = v.union(
  v.literal("ACTIVE"),
  v.literal("UPDATED"),
  v.literal("REMOVED"),
);

export const attentionLevelValidator = v.union(
  v.literal("LOW"),
  v.literal("NORMAL"),
  v.literal("IMPORTANT"),
  v.literal("URGENT"),
);

export const actionTypeValidator = v.union(
  v.literal("REPLY"),
  v.literal("DISMISS"),
  v.literal("SNOOZE"),
);

export const actionCapabilityValidator = v.union(
  actionTypeValidator,
  v.literal("MARK_READ"),
  v.literal("OPEN_APP"),
);

export const proposalStatusValidator = v.union(
  v.literal("PROPOSED"),
  v.literal("REVISED"),
  v.literal("CONFIRMED"),
  v.literal("COMMITTED"),
  v.literal("CANCELLED"),
  v.literal("EXPIRED"),
  v.literal("STALE"),
);

export const commandStatusValidator = v.union(
  v.literal("QUEUED"),
  v.literal("CLAIMED"),
  v.literal("EXECUTING"),
  v.literal("DISPATCHED"),
  v.literal("FAILED"),
  v.literal("EXPIRED"),
  v.literal("STALE"),
);

export const receiptStatusValidator = v.union(
  v.literal("DISPATCHED"),
  v.literal("FAILED"),
  v.literal("EXPIRED"),
  v.literal("STALE"),
);

export const textureCueValidator = v.union(
  v.literal("LISTENING_STARTED"),
  v.literal("CONTENT_MOVEMENT"),
  v.literal("FOCUS_ENTERED"),
  v.literal("ATTENTION_URGENT"),
  v.literal("PROPOSAL_READY"),
  v.literal("CONFIRMATION_REQUIRED"),
  v.literal("EXECUTION_STARTED"),
  v.literal("ACTION_DISPATCHED"),
  v.literal("ACTION_FAILED"),
  v.literal("CANCELLED"),
);

export const textureErrorCodeValidator = v.union(
  v.literal("NOTIFICATION_GONE"),
  v.literal("REPLY_NOT_SUPPORTED"),
  v.literal("ACTION_HANDLE_CHANGED"),
  v.literal("EVENT_CHANGED"),
  v.literal("PENDING_INTENT_CANCELLED"),
  v.literal("COMMAND_EXPIRED"),
  v.literal("UNAUTHORIZED"),
  v.literal("DUPLICATE_COMMAND"),
  v.literal("POLICY_BLOCKED"),
  v.literal("DEVICE_OFFLINE"),
  v.literal("NETWORK_ERROR"),
);

export const primitiveValidator = v.union(v.string(), v.number(), v.boolean());
export const payloadValidator = v.record(v.string(), primitiveValidator);
export const traceAttributesValidator = v.record(v.string(), primitiveValidator);

export const appValidator = v.object({
  packageName: v.string(),
  label: v.string(),
});

export const senderValidator = v.object({
  displayName: v.string(),
  personId: v.optional(v.string()),
});

export const priorityValidator = v.object({
  score: v.number(),
  level: attentionLevelValidator,
  reason: v.string(),
});

export const notificationEventInputValidator = v.object({
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
});

export const sourceModeValidator = v.union(v.literal("LIVE"), v.literal("REHEARSAL"));

export const deviceStatusValidator = v.union(
  v.literal("ONLINE"),
  v.literal("OFFLINE"),
  v.literal("REHEARSAL"),
);

export const platformValidator = v.union(v.literal("ANDROID"), v.literal("SIMULATOR"));

export const traceModeValidator = v.union(v.literal("LIVE"), v.literal("REHEARSAL"));

export const traceServiceValidator = v.union(
  v.literal("ANDROID_MOBILE"),
  v.literal("TEXTUREFLOW_CORE"),
  v.literal("TEXTUREFLOW_BRIDGE"),
  v.literal("TEXTURE_ENGINE"),
  v.literal("INTELLIGENCE"),
  v.literal("QA_HARNESS"),
);

export const traceOutcomeValidator = v.union(
  v.literal("OK"),
  v.literal("ERROR"),
  v.literal("SKIPPED"),
  v.literal("TIMEOUT"),
);

export const traceCorrelationValidator = v.object({
  eventId: v.optional(v.string()),
  eventVersion: v.optional(v.number()),
  proposalId: v.optional(v.string()),
  commandId: v.optional(v.string()),
  deviceId: v.optional(v.string()),
  sessionId: v.optional(v.string()),
});

export const traceNameValidator = v.union(
  v.literal("LISTENER_CONNECTED"),
  v.literal("ACTIVE_SNAPSHOT_STARTED"),
  v.literal("ACTIVE_SNAPSHOT_RECONCILED"),
  v.literal("LISTENER_DISCONNECTED"),
  v.literal("EVENT_RECEIVED"),
  v.literal("EVENT_UPDATED"),
  v.literal("EVENT_REMOVED"),
  v.literal("EVENT_STORED_LOCAL"),
  v.literal("OUTBOX_ENQUEUED"),
  v.literal("EVENT_SYNCED"),
  v.literal("ACTION_HANDLE_REGISTERED"),
  v.literal("ACTION_HANDLE_REMOVED"),
  v.literal("VOICE_TOOL_CALLED"),
  v.literal("ATTENTION_LISTED"),
  v.literal("PROPOSAL_CREATED"),
  v.literal("PROPOSAL_REVISED"),
  v.literal("PROPOSAL_CANCELLED"),
  v.literal("PROPOSAL_STALE"),
  v.literal("PROPOSAL_CONFIRMED"),
  v.literal("CONFIRMATION_REJECTED"),
  v.literal("COMMAND_QUEUED"),
  v.literal("COMMAND_CLAIMED"),
  v.literal("COMMAND_EXPIRED"),
  v.literal("POLICY_VALIDATED"),
  v.literal("ACTION_EXECUTION_STARTED"),
  v.literal("ACTION_DISPATCHED"),
  v.literal("ACTION_FAILED"),
  v.literal("RECEIPT_STORED_LOCAL"),
  v.literal("RECEIPT_SYNCED"),
  v.literal("CUE_SCHEDULED"),
  v.literal("CUE_RENDERED"),
  v.literal("CUE_SUPPRESSED_FOR_SPEECH"),
  v.literal("DEVICE_HEARTBEAT"),
  v.literal("REHEARSAL_STARTED"),
  v.literal("EVENT_INJECTED"),
  v.literal("UNTRUSTED_CONTENT_QUARANTINED"),
  v.literal("DUPLICATE_CONFIRMATION_IGNORED"),
  v.literal("DEVICE_EXECUTION_NOT_ATTEMPTED"),
  v.literal("REHEARSAL_COMPLETE"),
  v.literal("TRACE_LINK_MISSING"),
);
