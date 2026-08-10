import { z } from "zod";

const sessionId = z
  .string()
  .trim()
  .min(1)
  .max(128)
  .regex(/^[A-Za-z0-9._:-]+$/, "session_id contains unsupported characters")
  .default("voiceos-default");

const eventReference = z.string().trim().min(1).max(256);
const proposalReference = z.string().trim().min(1).max(256);
const message = z.string().trim().min(1).max(1_000);
const personName = z.string().trim().min(1).max(120);

export const StatusInputSchema = z.object({
  session_id: sessionId.optional()
}).strict();

export const ListAttentionInputSchema = z.object({
  session_id: sessionId.optional(),
  limit: z.number().int().min(1).max(10).default(3)
}).strict();

export const ReadEventInputSchema = z.object({
  session_id: sessionId.optional(),
  event_id: eventReference
}).strict();

export const MessagesFromInputSchema = z.object({
  session_id: sessionId.optional(),
  person_name: personName
}).strict();

export const PersonContextInputSchema = MessagesFromInputSchema;

export const PrepareReplyInputSchema = z.object({
  session_id: sessionId.optional(),
  event_id: eventReference,
  message
}).strict();

export const ReviseReplyInputSchema = z.object({
  session_id: sessionId.optional(),
  proposal_id: proposalReference.optional(),
  message
}).strict();

export const PrepareDismissInputSchema = z.object({
  session_id: sessionId.optional(),
  event_id: eventReference
}).strict();

export const PrepareSnoozeInputSchema = z.object({
  session_id: sessionId.optional(),
  event_id: eventReference,
  minutes: z.number().int().min(1).max(1_440)
}).strict();

export const ProposalActionInputSchema = z.object({
  session_id: sessionId.optional(),
  proposal_id: proposalReference.optional()
}).strict();

export type StatusInput = z.infer<typeof StatusInputSchema>;
export type ListAttentionInput = z.infer<typeof ListAttentionInputSchema>;
export type ReadEventInput = z.infer<typeof ReadEventInputSchema>;
export type MessagesFromInput = z.infer<typeof MessagesFromInputSchema>;
export type PrepareReplyInput = z.infer<typeof PrepareReplyInputSchema>;
export type ReviseReplyInput = z.infer<typeof ReviseReplyInputSchema>;
export type PrepareDismissInput = z.infer<typeof PrepareDismissInputSchema>;
export type PrepareSnoozeInput = z.infer<typeof PrepareSnoozeInputSchema>;
export type ProposalActionInput = z.infer<typeof ProposalActionInputSchema>;

export function withDefaultSession(sessionIdValue: string | undefined): string {
  return sessionIdValue ?? "voiceos-default";
}
