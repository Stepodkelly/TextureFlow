export const CONTRACT_VERSION = 1 as const;

export type EventStatus = "ACTIVE" | "UPDATED" | "REMOVED";
export type AttentionLevel = "LOW" | "NORMAL" | "IMPORTANT" | "URGENT";
export type ActionType = "REPLY" | "DISMISS" | "SNOOZE";
export type ActionCapability = ActionType | "MARK_READ" | "OPEN_APP";

export type ProposalStatus =
  | "PROPOSED"
  | "REVISED"
  | "CONFIRMED"
  | "COMMITTED"
  | "CANCELLED"
  | "EXPIRED"
  | "STALE";

export type CommandStatus =
  | "QUEUED"
  | "CLAIMED"
  | "EXECUTING"
  | "DISPATCHED"
  | "FAILED"
  | "EXPIRED"
  | "STALE";

export type ReceiptStatus = "DISPATCHED" | "FAILED" | "EXPIRED" | "STALE";

export type TextureCueName =
  | "LISTENING_STARTED"
  | "CONTENT_MOVEMENT"
  | "FOCUS_ENTERED"
  | "ATTENTION_URGENT"
  | "PROPOSAL_READY"
  | "CONFIRMATION_REQUIRED"
  | "EXECUTION_STARTED"
  | "ACTION_DISPATCHED"
  | "ACTION_FAILED"
  | "CANCELLED";

export type TextureErrorCode =
  | "NOTIFICATION_GONE"
  | "REPLY_NOT_SUPPORTED"
  | "ACTION_HANDLE_CHANGED"
  | "EVENT_CHANGED"
  | "PENDING_INTENT_CANCELLED"
  | "COMMAND_EXPIRED"
  | "UNAUTHORIZED"
  | "DUPLICATE_COMMAND"
  | "POLICY_BLOCKED"
  | "DEVICE_OFFLINE"
  | "NETWORK_ERROR";

export interface AttentionAssessment {
  score: number;
  level: AttentionLevel;
  reason: string;
}

export interface NotificationEvent {
  contractVersion: typeof CONTRACT_VERSION;
  eventId: string;
  deviceId: string;
  app: {
    packageName: string;
    label: string;
  };
  sender: {
    displayName: string;
    personId?: string;
  };
  conversationLabel?: string;
  body?: string;
  postedAt: string;
  updatedAt: string;
  version: number;
  status: EventStatus;
  capabilities: ActionCapability[];
  priority: AttentionAssessment;
}

export interface ActionProposal {
  contractVersion: typeof CONTRACT_VERSION;
  proposalId: string;
  ownerId: string;
  sessionId: string;
  eventId: string;
  expectedEventVersion: number;
  actionType: ActionType;
  payload: Record<string, string | number | boolean>;
  spokenPreview: string;
  status: ProposalStatus;
  createdAt: string;
  expiresAt: string;
}

export interface TextureCommand {
  contractVersion: typeof CONTRACT_VERSION;
  commandId: string;
  ownerId: string;
  proposalId: string;
  targetDeviceId: string;
  eventId: string;
  expectedEventVersion: number;
  actionType: ActionType;
  payload: Record<string, string | number | boolean>;
  idempotencyKey: string;
  status: CommandStatus;
  createdAt: string;
  expiresAt: string;
}

export interface ActionReceipt {
  contractVersion: typeof CONTRACT_VERSION;
  receiptId: string;
  commandId: string;
  deviceId: string;
  status: ReceiptStatus;
  errorCode?: TextureErrorCode;
  message: string;
  deviceTimestamp: string;
  textureCue: TextureCueName;
  traceId: string;
}

export interface TextureCue {
  contractVersion: typeof CONTRACT_VERSION;
  cue: TextureCueName;
  priority: "LOW" | "NORMAL" | "HIGH" | "CRITICAL";
  channels: Array<"AUDIO" | "HAPTIC" | "VISUAL">;
  speechPolicy: "ALLOW" | "DUCK_UNDER_SPEECH" | "SUPPRESS_UNDER_SPEECH";
  repeatPolicy: "ONCE" | "ONCE_PER_CORRELATION" | "RATE_LIMITED";
  correlationId: string;
}

export type TraceMode = "LIVE" | "REHEARSAL";
export type TraceService =
  | "ANDROID_MOBILE"
  | "TEXTUREFLOW_CORE"
  | "TEXTUREFLOW_BRIDGE"
  | "TEXTURE_ENGINE"
  | "INTELLIGENCE"
  | "QA_HARNESS";
export type TraceOutcome = "OK" | "ERROR" | "SKIPPED" | "TIMEOUT";

export interface TraceEvent {
  contractVersion: typeof CONTRACT_VERSION;
  mode: TraceMode;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  sequence?: number;
  name: string;
  service: TraceService;
  outcome: TraceOutcome;
  occurredAt: string;
  durationMs?: number;
  correlation: {
    eventId?: string;
    eventVersion?: number;
    proposalId?: string;
    commandId?: string;
    deviceId?: string;
    sessionId?: string;
  };
  attributes: Record<string, string | number | boolean>;
}

export interface ToolEnvelope<T> {
  ok: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
  spokenSummary: string;
  requiresConfirmation: boolean;
  proposalId?: string;
  textureCue?: TextureCueName;
  traceId: string;
}
