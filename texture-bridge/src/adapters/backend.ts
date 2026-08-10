import type {
  ActionProposal,
  ActionType,
  NotificationEvent
} from "../contracts.js";
import type {
  BridgeStatus,
  ConfirmationResult,
  PersonContext
} from "../types.js";

export interface RequestContext {
  ownerId: string;
  sessionId: string;
  traceId: string;
}

export interface PrepareActionInput extends RequestContext {
  eventId: string;
  actionType: ActionType;
  payload: Record<string, string | number | boolean>;
}

export interface TextureBackend {
  getStatus(context: RequestContext): Promise<BridgeStatus>;
  listAttention(context: RequestContext, limit: number): Promise<NotificationEvent[]>;
  readEvent(context: RequestContext, eventId: string): Promise<NotificationEvent>;
  messagesFrom(context: RequestContext, personName: string): Promise<NotificationEvent[]>;
  personContext(context: RequestContext, personName: string): Promise<PersonContext>;
  prepareAction(input: PrepareActionInput): Promise<ActionProposal>;
  reviseReply(
    context: RequestContext,
    proposalId: string,
    message: string
  ): Promise<ActionProposal>;
  cancelProposal(context: RequestContext, proposalId: string): Promise<ActionProposal>;
  confirmProposal(
    context: RequestContext,
    proposalId: string
  ): Promise<ConfirmationResult>;
}
