import type { ActionProposal, NotificationEvent } from "./contracts.js";
import { BridgeError } from "./errors.js";

interface Reference {
  id: string;
  expiresAt: number;
}

interface SessionState {
  expiresAt: number;
  eventReferences: Map<string, Reference>;
  personReferences: Map<string, Reference>;
  proposalReferences: Map<string, Reference>;
  activeProposalId?: string;
}

export interface SessionStoreOptions {
  now?: () => number;
  ttlMs?: number;
}

export class SessionStore {
  private readonly sessions = new Map<string, SessionState>();
  private readonly now: () => number;
  private readonly ttlMs: number;

  constructor(options: SessionStoreOptions = {}) {
    this.now = options.now ?? (() => Date.now());
    this.ttlMs = options.ttlMs ?? 120_000;
  }

  recordEvents(sessionId: string, events: NotificationEvent[]): void {
    if (events.length === 0) {
      return;
    }
    const state = this.state(sessionId);
    events.forEach((event, index) => {
      this.setReference(state.eventReferences, event.eventId, event.eventId);
      this.setReference(state.eventReferences, event.sender.displayName, event.eventId);
      if (event.sender.personId) {
        this.setReference(state.personReferences, event.sender.displayName, event.sender.personId);
      }
      if (index === 0) {
        for (const alias of ["that one", "the first one", "first", "latest"]) {
          this.setReference(state.eventReferences, alias, event.eventId);
        }
      }
    });
    this.touch(state);
  }

  recordPerson(sessionId: string, personName: string, personId: string): void {
    const state = this.state(sessionId);
    this.setReference(state.personReferences, personName, personId);
    this.touch(state);
  }

  recordProposal(sessionId: string, proposal: ActionProposal): void {
    const state = this.state(sessionId);
    state.activeProposalId = proposal.proposalId;
    for (const alias of [proposal.proposalId, "it", "send it", "that action", "the proposal"]) {
      this.setReference(state.proposalReferences, alias, proposal.proposalId);
    }
    this.touch(state);
  }

  resolveEvent(sessionId: string, reference: string): string {
    const state = this.sessions.get(sessionId);
    const resolved = state && this.resolveReference(state.eventReferences, reference);
    if (resolved) {
      this.touch(state);
      return resolved;
    }

    if (this.looksLikeStableId(reference)) {
      return reference;
    }
    throw new BridgeError(
      "REFERENCE_NOT_FOUND",
      `I cannot tell which notification “${reference}” refers to. Please list attention again.`
    );
  }

  resolveProposal(sessionId: string, reference?: string): string {
    const state = this.sessions.get(sessionId);
    if (!state || this.isExpired(state.expiresAt)) {
      this.sessions.delete(sessionId);
      throw new BridgeError(
        "PROPOSAL_NOT_FOUND",
        "There is no active proposal in this voice session."
      );
    }

    if (reference) {
      const resolved = this.resolveReference(state.proposalReferences, reference);
      if (resolved) {
        this.touch(state);
        return resolved;
      }
      if (this.looksLikeStableId(reference)) {
        return reference;
      }
      throw new BridgeError("PROPOSAL_NOT_FOUND", "That proposal is not active in this voice session.");
    }

    if (!state.activeProposalId) {
      throw new BridgeError(
        "PROPOSAL_NOT_FOUND",
        "There is no active proposal in this voice session."
      );
    }
    this.touch(state);
    return state.activeProposalId;
  }

  clearProposal(sessionId: string, proposalId: string): void {
    const state = this.sessions.get(sessionId);
    if (!state) {
      return;
    }
    if (state.activeProposalId === proposalId) {
      delete state.activeProposalId;
    }
    for (const [alias, reference] of state.proposalReferences) {
      if (reference.id === proposalId) {
        state.proposalReferences.delete(alias);
      }
    }
  }

  private state(sessionId: string): SessionState {
    const existing = this.sessions.get(sessionId);
    if (existing && !this.isExpired(existing.expiresAt)) {
      return existing;
    }
    const created: SessionState = {
      expiresAt: this.now() + this.ttlMs,
      eventReferences: new Map(),
      personReferences: new Map(),
      proposalReferences: new Map()
    };
    this.sessions.set(sessionId, created);
    return created;
  }

  private setReference(map: Map<string, Reference>, alias: string, id: string): void {
    map.set(this.normalize(alias), { id, expiresAt: this.now() + this.ttlMs });
  }

  private resolveReference(map: Map<string, Reference>, alias: string): string | undefined {
    const key = this.normalize(alias);
    const reference = map.get(key);
    if (!reference) {
      return undefined;
    }
    if (this.isExpired(reference.expiresAt)) {
      map.delete(key);
      return undefined;
    }
    return reference.id;
  }

  private normalize(value: string): string {
    return value.trim().toLocaleLowerCase();
  }

  private looksLikeStableId(value: string): boolean {
    return /^[A-Za-z0-9][A-Za-z0-9._:-]{2,255}$/.test(value);
  }

  private touch(state: SessionState): void {
    state.expiresAt = this.now() + this.ttlMs;
  }

  private isExpired(expiresAt: number): boolean {
    return expiresAt <= this.now();
  }
}
