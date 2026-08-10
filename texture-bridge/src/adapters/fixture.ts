import {
  CONTRACT_VERSION,
  type ActionProposal,
  type ActionReceipt,
  type NotificationEvent,
  type TextureCommand
} from "../contracts.js";
import { BridgeError } from "../errors.js";
import { buildFixtureEvents } from "../fixtures/data.js";
import type { BridgeStatus, ConfirmationResult, PersonContext } from "../types.js";
import type {
  PrepareActionInput,
  RequestContext,
  TextureBackend
} from "./backend.js";

export interface FixtureAdapterOptions {
  now?: () => Date;
  proposalTtlMs?: number;
}

export class FixtureAdapter implements TextureBackend {
  private readonly now: () => Date;
  private readonly proposalTtlMs: number;
  private readonly events = new Map<string, NotificationEvent>();
  private readonly proposals = new Map<string, ActionProposal>();
  private readonly confirmations = new Map<string, ConfirmationResult>();
  private proposalSequence = 0;
  private commandSequence = 0;

  constructor(options: FixtureAdapterOptions = {}) {
    this.now = options.now ?? (() => new Date());
    this.proposalTtlMs = options.proposalTtlMs ?? 60_000;
    for (const event of buildFixtureEvents(this.now())) {
      this.events.set(event.eventId, structuredClone(event));
    }
  }

  async getStatus(_context: RequestContext): Promise<BridgeStatus> {
    return {
      mode: "REHEARSAL",
      bridge: "ONLINE",
      device: {
        deviceId: "pixel_fixture",
        label: "Fixture Pixel",
        online: true,
        stale: false,
        lastSeenAt: this.now().toISOString()
      },
      activeEventCount: this.activeEvents().length,
      pendingProposalCount: [...this.proposals.values()].filter((proposal) =>
        proposal.status === "PROPOSED" || proposal.status === "REVISED"
      ).length
    };
  }

  async listAttention(
    _context: RequestContext,
    limit: number
  ): Promise<NotificationEvent[]> {
    return this.activeEvents()
      .sort((left, right) => right.priority.score - left.priority.score)
      .slice(0, limit)
      .map((event) => structuredClone(event));
  }

  async readEvent(
    _context: RequestContext,
    eventId: string
  ): Promise<NotificationEvent> {
    return structuredClone(this.requireActiveEvent(eventId));
  }

  async messagesFrom(
    _context: RequestContext,
    personName: string
  ): Promise<NotificationEvent[]> {
    const normalized = personName.trim().toLocaleLowerCase();
    const events = this.activeEvents().filter(
      (event) => event.sender.displayName.toLocaleLowerCase() === normalized
    );
    if (events.length === 0) {
      throw new BridgeError("PERSON_NOT_FOUND", `No active messages were found for ${personName}.`);
    }
    return events.map((event) => structuredClone(event));
  }

  async personContext(
    context: RequestContext,
    personName: string
  ): Promise<PersonContext> {
    const events = await this.messagesFrom(context, personName);
    const personId = events[0]?.sender.personId;
    if (!personId) {
      throw new BridgeError("PERSON_NOT_FOUND", `No person context was found for ${personName}.`);
    }

    const identities = events.map((event) => ({
      appLabel: event.app.label,
      handle: event.conversationLabel ?? event.sender.displayName
    }));
    return {
      personId,
      displayName: events[0]?.sender.displayName ?? personName,
      identities,
      summary: `${events[0]?.sender.displayName ?? personName} has ${events.length} active request${events.length === 1 ? "" : "s"}.`,
      openRequests: events.flatMap((event) => event.body ? [event.body] : []),
      recentEvents: events
    };
  }

  async prepareAction(input: PrepareActionInput): Promise<ActionProposal> {
    const event = this.requireActiveEvent(input.eventId);
    if (!event.capabilities.includes(input.actionType)) {
      const code = input.actionType === "REPLY" ? "REPLY_NOT_SUPPORTED" : "POLICY_BLOCKED";
      throw new BridgeError(code, `${event.app.label} does not expose ${input.actionType.toLowerCase()} for this event.`);
    }

    const message = input.actionType === "REPLY" ? String(input.payload.message ?? "").trim() : undefined;
    if (input.actionType === "REPLY" && !message) {
      throw new BridgeError("INVALID_INPUT", "A reply message is required.");
    }

    const createdAt = this.now();
    const proposalId = `prop_fixture_${String(++this.proposalSequence).padStart(4, "0")}`;
    const proposal: ActionProposal = {
      contractVersion: CONTRACT_VERSION,
      proposalId,
      ownerId: input.ownerId,
      sessionId: input.sessionId,
      eventId: event.eventId,
      expectedEventVersion: event.version,
      actionType: input.actionType,
      payload: structuredClone(input.payload),
      spokenPreview: this.preview(event, input.actionType, input.payload),
      status: "PROPOSED",
      createdAt: createdAt.toISOString(),
      expiresAt: new Date(createdAt.getTime() + this.proposalTtlMs).toISOString()
    };
    this.proposals.set(proposalId, proposal);
    return structuredClone(proposal);
  }

  async reviseReply(
    context: RequestContext,
    proposalId: string,
    message: string
  ): Promise<ActionProposal> {
    const proposal = this.requireMutableProposal(context, proposalId);
    if (proposal.actionType !== "REPLY") {
      throw new BridgeError("POLICY_BLOCKED", "Only reply proposals can be revised.");
    }
    const event = this.requireCurrentEvent(proposal);
    const revised: ActionProposal = {
      ...proposal,
      payload: { message },
      spokenPreview: this.preview(event, "REPLY", { message }),
      status: "REVISED"
    };
    this.proposals.set(proposalId, revised);
    return structuredClone(revised);
  }

  async cancelProposal(
    context: RequestContext,
    proposalId: string
  ): Promise<ActionProposal> {
    const proposal = this.requireMutableProposal(context, proposalId);
    const cancelled: ActionProposal = { ...proposal, status: "CANCELLED" };
    this.proposals.set(proposalId, cancelled);
    return structuredClone(cancelled);
  }

  async confirmProposal(
    context: RequestContext,
    proposalId: string
  ): Promise<ConfirmationResult> {
    const prior = this.confirmations.get(proposalId);
    if (prior) {
      return structuredClone(prior);
    }

    const proposal = this.requireMutableProposal(context, proposalId);
    const event = this.requireCurrentEvent(proposal);
    const now = this.now();
    const commandId = `cmd_fixture_${String(++this.commandSequence).padStart(4, "0")}`;
    const confirmed: ActionProposal = { ...proposal, status: "CONFIRMED" };
    const command: TextureCommand = {
      contractVersion: CONTRACT_VERSION,
      commandId,
      ownerId: proposal.ownerId,
      proposalId,
      targetDeviceId: event.deviceId,
      eventId: event.eventId,
      expectedEventVersion: proposal.expectedEventVersion,
      actionType: proposal.actionType,
      payload: structuredClone(proposal.payload),
      idempotencyKey: `${proposalId}:confirm:v1`,
      status: "DISPATCHED",
      createdAt: now.toISOString(),
      expiresAt: proposal.expiresAt
    };
    const receipt: ActionReceipt = {
      contractVersion: CONTRACT_VERSION,
      receiptId: `receipt_${commandId}`,
      commandId,
      deviceId: event.deviceId,
      status: "DISPATCHED",
      message: `${proposal.actionType} dispatched in rehearsal mode.`,
      deviceTimestamp: now.toISOString(),
      textureCue: "ACTION_DISPATCHED",
      traceId: context.traceId
    };
    const committed: ActionProposal = { ...confirmed, status: "COMMITTED" };
    this.proposals.set(proposalId, committed);
    if (proposal.actionType === "DISMISS" || proposal.actionType === "SNOOZE") {
      this.events.set(event.eventId, { ...event, status: "REMOVED" });
    }

    const result: ConfirmationResult = {
      proposal: committed,
      command,
      event: structuredClone(event),
      receipt
    };
    this.confirmations.set(proposalId, result);
    return structuredClone(result);
  }

  replaceEventForTest(event: NotificationEvent): void {
    this.events.set(event.eventId, structuredClone(event));
  }

  private activeEvents(): NotificationEvent[] {
    return [...this.events.values()].filter((event) => event.status !== "REMOVED");
  }

  private requireActiveEvent(eventId: string): NotificationEvent {
    const event = this.events.get(eventId);
    if (!event || event.status === "REMOVED") {
      throw new BridgeError("NOTIFICATION_GONE", "That notification is no longer active.");
    }
    return event;
  }

  private requireMutableProposal(
    context: RequestContext,
    proposalId: string
  ): ActionProposal {
    const proposal = this.proposals.get(proposalId);
    if (!proposal || proposal.ownerId !== context.ownerId || proposal.sessionId !== context.sessionId) {
      throw new BridgeError("PROPOSAL_NOT_FOUND", "That proposal is not active in this voice session.");
    }
    if (new Date(proposal.expiresAt).getTime() <= this.now().getTime()) {
      this.proposals.set(proposalId, { ...proposal, status: "EXPIRED" });
      throw new BridgeError("COMMAND_EXPIRED", "That proposal expired. Please prepare it again.");
    }
    if (proposal.status !== "PROPOSED" && proposal.status !== "REVISED") {
      throw new BridgeError("POLICY_BLOCKED", `The proposal is already ${proposal.status.toLowerCase()}.`);
    }
    return proposal;
  }

  private requireCurrentEvent(proposal: ActionProposal): NotificationEvent {
    const event = this.requireActiveEvent(proposal.eventId);
    if (event.version !== proposal.expectedEventVersion) {
      this.proposals.set(proposal.proposalId, { ...proposal, status: "STALE" });
      throw new BridgeError("EVENT_CHANGED", "The notification changed after this action was prepared.");
    }
    return event;
  }

  private preview(
    event: NotificationEvent,
    actionType: ActionProposal["actionType"],
    payload: Record<string, string | number | boolean>
  ): string {
    if (actionType === "REPLY") {
      return `Reply to ${event.sender.displayName} on ${event.app.label}: ${String(payload.message)}.`;
    }
    if (actionType === "SNOOZE") {
      return `Snooze ${event.sender.displayName}'s ${event.app.label} notification for ${String(payload.minutes)} minutes.`;
    }
    return `Dismiss ${event.sender.displayName}'s ${event.app.label} notification.`;
  }
}
