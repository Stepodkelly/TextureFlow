import { z } from "zod";

export const CONTRACT_VERSION = 1 as const;

export const EventStatusSchema = z.enum(["ACTIVE", "UPDATED", "REMOVED"]);
export const AttentionLevelSchema = z.enum([
  "LOW",
  "NORMAL",
  "IMPORTANT",
  "URGENT"
]);
export const ActionTypeSchema = z.enum(["REPLY", "DISMISS", "SNOOZE"]);
export const ActionCapabilitySchema = z.enum([
  "REPLY",
  "DISMISS",
  "SNOOZE",
  "MARK_READ",
  "OPEN_APP"
]);
export const ProposalStatusSchema = z.enum([
  "PROPOSED",
  "REVISED",
  "CONFIRMED",
  "COMMITTED",
  "CANCELLED",
  "EXPIRED",
  "STALE"
]);
export const CommandStatusSchema = z.enum([
  "QUEUED",
  "CLAIMED",
  "EXECUTING",
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE"
]);
export const ReceiptStatusSchema = z.enum([
  "DISPATCHED",
  "FAILED",
  "EXPIRED",
  "STALE"
]);
export const TextureCueNameSchema = z.enum([
  "LISTENING_STARTED",
  "CONTENT_MOVEMENT",
  "FOCUS_ENTERED",
  "ATTENTION_URGENT",
  "PROPOSAL_READY",
  "CONFIRMATION_REQUIRED",
  "EXECUTION_STARTED",
  "ACTION_DISPATCHED",
  "ACTION_FAILED",
  "CANCELLED"
]);
export const TextureErrorCodeSchema = z.enum([
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
  "NETWORK_ERROR"
]);

const PayloadValueSchema = z.union([z.string(), z.number(), z.boolean()]);

export const AttentionAssessmentSchema = z.object({
  score: z.number().min(0).max(1),
  level: AttentionLevelSchema,
  reason: z.string().min(1)
});

export const NotificationEventSchema = z.object({
  contractVersion: z.literal(CONTRACT_VERSION),
  eventId: z.string().min(1),
  deviceId: z.string().min(1),
  app: z.object({
    packageName: z.string().min(1),
    label: z.string().min(1)
  }),
  sender: z.object({
    displayName: z.string().min(1),
    personId: z.string().min(1).optional()
  }),
  conversationLabel: z.string().min(1).optional(),
  body: z.string().optional(),
  postedAt: z.string().min(1),
  updatedAt: z.string().min(1),
  version: z.number().int().positive(),
  status: EventStatusSchema,
  capabilities: z.array(ActionCapabilitySchema),
  priority: AttentionAssessmentSchema
});

export const ActionProposalSchema = z.object({
  contractVersion: z.literal(CONTRACT_VERSION),
  proposalId: z.string().min(1),
  ownerId: z.string().min(1),
  sessionId: z.string().min(1),
  eventId: z.string().min(1),
  expectedEventVersion: z.number().int().positive(),
  actionType: ActionTypeSchema,
  payload: z.record(z.string(), PayloadValueSchema),
  spokenPreview: z.string().min(1),
  status: ProposalStatusSchema,
  createdAt: z.string().min(1),
  expiresAt: z.string().min(1)
});

export const TextureCommandSchema = z.object({
  contractVersion: z.literal(CONTRACT_VERSION),
  commandId: z.string().min(1),
  ownerId: z.string().min(1),
  proposalId: z.string().min(1),
  targetDeviceId: z.string().min(1),
  eventId: z.string().min(1),
  expectedEventVersion: z.number().int().positive(),
  actionType: ActionTypeSchema,
  payload: z.record(z.string(), PayloadValueSchema),
  idempotencyKey: z.string().min(1),
  status: CommandStatusSchema,
  createdAt: z.string().min(1),
  expiresAt: z.string().min(1)
});

export const ActionReceiptSchema = z.object({
  contractVersion: z.literal(CONTRACT_VERSION),
  receiptId: z.string().min(1),
  commandId: z.string().min(1),
  deviceId: z.string().min(1),
  status: ReceiptStatusSchema,
  errorCode: TextureErrorCodeSchema.optional(),
  message: z.string(),
  deviceTimestamp: z.string().min(1),
  textureCue: TextureCueNameSchema,
  traceId: z.string().min(1)
});

export type NotificationEvent = z.infer<typeof NotificationEventSchema>;
export type ActionProposal = z.infer<typeof ActionProposalSchema>;
export type TextureCommand = z.infer<typeof TextureCommandSchema>;
export type ActionReceipt = z.infer<typeof ActionReceiptSchema>;
export type ActionType = z.infer<typeof ActionTypeSchema>;
export type TextureCueName = z.infer<typeof TextureCueNameSchema>;

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
