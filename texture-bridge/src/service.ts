import { randomUUID } from "node:crypto";
import type { TextureBackend } from "./adapters/backend.js";
import type { ToolEnvelope } from "./contracts.js";
import { normalizeError } from "./errors.js";
import {
  formatAttention,
  formatCancellation,
  formatEvent,
  formatMessages,
  formatPersonContext,
  formatProposal,
  formatReceipt,
  formatStatus
} from "./formatter.js";
import type { Logger } from "./logger.js";
import { SessionStore } from "./session.js";
import {
  ListAttentionInputSchema,
  MessagesFromInputSchema,
  PersonContextInputSchema,
  PrepareDismissInputSchema,
  PrepareReplyInputSchema,
  PrepareSnoozeInputSchema,
  ProposalActionInputSchema,
  ReadEventInputSchema,
  ReviseReplyInputSchema,
  StatusInputSchema,
  withDefaultSession
} from "./tools/schemas.js";

export interface TraceIdSource {
  next(): string;
}

export const randomTraceIds: TraceIdSource = {
  next: () => `trace_${randomUUID()}`
};

export interface BridgeServiceOptions {
  backend: TextureBackend;
  ownerId: string;
  sessions: SessionStore;
  logger: Logger;
  traceIds?: TraceIdSource;
}

export class BridgeService {
  private readonly backend: TextureBackend;
  private readonly ownerId: string;
  private readonly sessions: SessionStore;
  private readonly logger: Logger;
  private readonly traceIds: TraceIdSource;

  constructor(options: BridgeServiceOptions) {
    this.backend = options.backend;
    this.ownerId = options.ownerId;
    this.sessions = options.sessions;
    this.logger = options.logger;
    this.traceIds = options.traceIds ?? randomTraceIds;
  }

  status(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_status", async (traceId) => {
      const parsed = StatusInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const status = await this.backend.getStatus(this.context(sessionId, traceId));
      return this.success({
        data: { status },
        spokenSummary: formatStatus(status),
        traceId
      });
    });
  }

  listAttention(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_list_attention", async (traceId) => {
      const parsed = ListAttentionInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const events = await this.backend.listAttention(
        this.context(sessionId, traceId),
        parsed.limit
      );
      this.sessions.recordEvents(sessionId, events);
      return this.success({
        data: { events },
        spokenSummary: formatAttention(events),
        textureCue: events.some((event) => event.priority.level === "URGENT")
          ? "ATTENTION_URGENT"
          : "CONTENT_MOVEMENT",
        traceId
      });
    });
  }

  readEvent(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_read_event", async (traceId) => {
      const parsed = ReadEventInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const eventId = this.sessions.resolveEvent(sessionId, parsed.event_id);
      const event = await this.backend.readEvent(this.context(sessionId, traceId), eventId);
      this.sessions.recordEvents(sessionId, [event]);
      return this.success({
        data: { event },
        spokenSummary: formatEvent(event),
        textureCue: event.priority.level === "URGENT" ? "ATTENTION_URGENT" : "FOCUS_ENTERED",
        traceId
      });
    });
  }

  messagesFrom(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_messages_from", async (traceId) => {
      const parsed = MessagesFromInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const events = await this.backend.messagesFrom(
        this.context(sessionId, traceId),
        parsed.person_name
      );
      this.sessions.recordEvents(sessionId, events);
      return this.success({
        data: { events },
        spokenSummary: formatMessages(parsed.person_name, events),
        textureCue: "CONTENT_MOVEMENT",
        traceId
      });
    });
  }

  personContext(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_person_context", async (traceId) => {
      const parsed = PersonContextInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const personContext = await this.backend.personContext(
        this.context(sessionId, traceId),
        parsed.person_name
      );
      this.sessions.recordPerson(sessionId, parsed.person_name, personContext.personId);
      this.sessions.recordEvents(sessionId, personContext.recentEvents);
      return this.success({
        data: { personContext },
        spokenSummary: formatPersonContext(personContext),
        textureCue: "CONTENT_MOVEMENT",
        traceId
      });
    });
  }

  prepareReply(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_prepare_reply", async (traceId) => {
      const parsed = PrepareReplyInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const eventId = this.sessions.resolveEvent(sessionId, parsed.event_id);
      const proposal = await this.backend.prepareAction({
        ...this.context(sessionId, traceId),
        eventId,
        actionType: "REPLY",
        payload: { message: parsed.message }
      });
      this.sessions.recordProposal(sessionId, proposal);
      return this.proposalEnvelope(proposal, traceId);
    });
  }

  reviseReply(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_revise_reply", async (traceId) => {
      const parsed = ReviseReplyInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const proposalId = this.sessions.resolveProposal(sessionId, parsed.proposal_id);
      const proposal = await this.backend.reviseReply(
        this.context(sessionId, traceId),
        proposalId,
        parsed.message
      );
      this.sessions.recordProposal(sessionId, proposal);
      return this.proposalEnvelope(proposal, traceId);
    });
  }

  prepareDismiss(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_prepare_dismiss", async (traceId) => {
      const parsed = PrepareDismissInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const eventId = this.sessions.resolveEvent(sessionId, parsed.event_id);
      const proposal = await this.backend.prepareAction({
        ...this.context(sessionId, traceId),
        eventId,
        actionType: "DISMISS",
        payload: {}
      });
      this.sessions.recordProposal(sessionId, proposal);
      return this.proposalEnvelope(proposal, traceId);
    });
  }

  prepareSnooze(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_prepare_snooze", async (traceId) => {
      const parsed = PrepareSnoozeInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const eventId = this.sessions.resolveEvent(sessionId, parsed.event_id);
      const proposal = await this.backend.prepareAction({
        ...this.context(sessionId, traceId),
        eventId,
        actionType: "SNOOZE",
        payload: { minutes: parsed.minutes }
      });
      this.sessions.recordProposal(sessionId, proposal);
      return this.proposalEnvelope(proposal, traceId);
    });
  }

  confirmAction(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_confirm_action", async (traceId) => {
      const parsed = ProposalActionInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const proposalId = this.sessions.resolveProposal(sessionId, parsed.proposal_id);
      const result = await this.backend.confirmProposal(
        this.context(sessionId, traceId),
        proposalId
      );
      this.sessions.clearProposal(sessionId, proposalId);

      if (!result.receipt) {
        return this.success({
          data: {
            proposal: result.proposal,
            command: result.command,
            receipt: null
          },
          spokenSummary: "The action was confirmed and queued. TextureFlow is still waiting for the phone's execution receipt.",
          textureCue: "EXECUTION_STARTED",
          traceId
        });
      }

      if (result.receipt.status !== "DISPATCHED") {
        return {
          ok: false,
          data: result,
          error: {
            code: result.receipt.errorCode ?? result.receipt.status,
            message: result.receipt.message
          },
          spokenSummary: formatReceipt(
            result.event,
            result.proposal,
            result.receipt
          ),
          requiresConfirmation: false,
          textureCue: "ACTION_FAILED",
          traceId
        };
      }

      return this.success({
        data: result,
        spokenSummary: formatReceipt(result.event, result.proposal, result.receipt),
        textureCue: "ACTION_DISPATCHED",
        traceId
      });
    });
  }

  cancelAction(input: unknown): Promise<ToolEnvelope<unknown>> {
    return this.run("texture_cancel_action", async (traceId) => {
      const parsed = ProposalActionInputSchema.parse(input);
      const sessionId = withDefaultSession(parsed.session_id);
      const proposalId = this.sessions.resolveProposal(sessionId, parsed.proposal_id);
      const proposal = await this.backend.cancelProposal(
        this.context(sessionId, traceId),
        proposalId
      );
      this.sessions.clearProposal(sessionId, proposalId);
      return this.success({
        data: { proposal },
        spokenSummary: formatCancellation(proposal),
        textureCue: "CANCELLED",
        traceId
      });
    });
  }

  private context(sessionId: string, traceId: string) {
    return {
      ownerId: this.ownerId,
      sessionId,
      traceId
    };
  }

  private proposalEnvelope(proposal: Parameters<typeof formatProposal>[0], traceId: string) {
    return this.success({
      data: { proposal },
      spokenSummary: formatProposal(proposal),
      requiresConfirmation: true,
      proposalId: proposal.proposalId,
      textureCue: "CONFIRMATION_REQUIRED",
      traceId
    });
  }

  private success(options: {
    data: unknown;
    spokenSummary: string;
    traceId: string;
    requiresConfirmation?: boolean;
    proposalId?: string;
    textureCue?: ToolEnvelope<unknown>["textureCue"];
  }): ToolEnvelope<unknown> {
    return {
      ok: true,
      data: options.data,
      spokenSummary: options.spokenSummary,
      requiresConfirmation: options.requiresConfirmation ?? false,
      ...(options.proposalId ? { proposalId: options.proposalId } : {}),
      ...(options.textureCue ? { textureCue: options.textureCue } : {}),
      traceId: options.traceId
    };
  }

  private async run(
    toolName: string,
    operation: (traceId: string) => Promise<ToolEnvelope<unknown>>
  ): Promise<ToolEnvelope<unknown>> {
    const traceId = this.traceIds.next();
    try {
      const envelope = await operation(traceId);
      this.logger.log("info", "tool_complete", {
        tool: toolName,
        traceId,
        ok: envelope.ok,
        requiresConfirmation: envelope.requiresConfirmation
      });
      return envelope;
    } catch (error) {
      const normalized = normalizeError(error);
      this.logger.log("warn", "tool_failed", {
        tool: toolName,
        traceId,
        code: normalized.code
      });
      return {
        ok: false,
        error: {
          code: normalized.code,
          message: normalized.message
        },
        spokenSummary: normalized.message,
        requiresConfirmation: false,
        textureCue: "ACTION_FAILED",
        traceId
      };
    }
  }
}
