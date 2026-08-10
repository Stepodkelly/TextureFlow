import { z } from "zod";
import {
  ActionProposalSchema,
  ActionReceiptSchema,
  NotificationEventSchema,
  TextureCommandSchema
} from "./contracts.js";

export const RuntimeModeSchema = z.enum(["LIVE", "REHEARSAL"]);

export const BridgeStatusSchema = z.object({
  mode: RuntimeModeSchema,
  bridge: z.literal("ONLINE"),
  device: z.object({
    deviceId: z.string().min(1),
    label: z.string().min(1),
    online: z.boolean(),
    stale: z.boolean(),
    lastSeenAt: z.string().min(1)
  }),
  activeEventCount: z.number().int().nonnegative(),
  pendingProposalCount: z.number().int().nonnegative()
});

export const PersonContextSchema = z.object({
  personId: z.string().min(1),
  displayName: z.string().min(1),
  identities: z.array(
    z.object({
      appLabel: z.string().min(1),
      handle: z.string().min(1)
    })
  ),
  summary: z.string().min(1),
  openRequests: z.array(z.string()),
  recentEvents: z.array(NotificationEventSchema)
});

export const ConfirmationResultSchema = z.object({
  proposal: ActionProposalSchema,
  command: TextureCommandSchema,
  event: NotificationEventSchema,
  receipt: ActionReceiptSchema.optional()
});

export type BridgeStatus = z.infer<typeof BridgeStatusSchema>;
export type PersonContext = z.infer<typeof PersonContextSchema>;
export type ConfirmationResult = z.infer<typeof ConfirmationResultSchema>;
