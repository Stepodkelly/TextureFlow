import { PersonAliasResolver } from "./aliases.js";
import { buildBoundedPersonContext, wrapUntrustedNotification } from "./context.js";
import { DeterministicIntelligence } from "./fallback.js";
import { OpenAIResponsesAdapter, type OpenAIResponsesAdapterOptions } from "./openaiResponsesAdapter.js";
import {
  assessPriority,
  containsUntrustedInstruction,
  mergeModelPriority,
} from "./priority.js";
import {
  parseGeneratedDraftReply,
  parseGeneratedSummary,
} from "./schemas.js";
import type {
  DraftReplyRequest,
  DraftReplyResult,
  GeneratedSummary,
  IdentityResolution,
  IntelligencePort,
  NotificationEvent,
  SummaryRequest,
  SummaryResult,
} from "./types.js";

export interface IntelligenceServiceOptions {
  primary?: IntelligencePort;
  fallback?: IntelligencePort;
  aliases?: PersonAliasResolver;
}

export class IntelligenceService {
  private readonly primary: IntelligencePort | undefined;
  private readonly fallback: IntelligencePort;
  private readonly aliases: PersonAliasResolver;

  constructor(options: IntelligenceServiceOptions = {}) {
    this.primary = options.primary;
    this.fallback = options.fallback ?? new DeterministicIntelligence();
    this.aliases = options.aliases ?? new PersonAliasResolver();
  }

  async summarize(request: SummaryRequest): Promise<SummaryResult> {
    const activeEvents = request.events.filter((event) => event.status !== "REMOVED").slice(0, 20);
    if (activeEvents.length === 0) {
      return {
        summary: "There are no active notifications to summarize.",
        priorityScore: 0,
        priorityLevel: "LOW",
        priorityReason: "No active notification was supplied.",
        intent: "UNKNOWN",
        requiresResponse: false,
        ambiguities: [],
        source: "DETERMINISTIC_FALLBACK",
        eventIds: [],
        safety: { containedUntrustedInstructions: false },
      };
    }

    const now = request.now ? new Date(request.now) : new Date();
    const ranked = activeEvents
      .map((event) => {
        const identity = this.aliases.resolveEvent(event);
        return { event, identity, priority: assessPriority(event, identity, now) };
      })
      .sort((left, right) => right.priority.assessment.score - left.priority.assessment.score);
    const lead = ranked[0]!;
    const context = buildBoundedPersonContext(
      activeEvents.filter((event) => samePerson(event, lead.identity, this.aliases)),
      lead.identity,
      undefined,
      this.aliases,
    );
    const modelInput = {
      userRequest: request.userRequest ?? "Summarize what needs attention.",
      trustedContext: context,
      deterministicPriority: lead.priority.assessment,
      notifications: ranked.slice(0, 5).map(({ event }) => wrapUntrustedNotification(event)),
    };
    const malicious = ranked.some(({ priority }) => priority.features.maliciousInstruction);
    const shouldCallPrimary = !malicious && Boolean(this.primary) && shouldUseModel(
      ranked.map(({ event }) => event),
      lead.priority.assessment.score,
      ranked.every(({ priority }) => priority.features.promotional),
      request.forceModel ?? false,
    );

    let generated: GeneratedSummary;
    let source: SummaryResult["source"] = "DETERMINISTIC_FALLBACK";
    let modelFailure: string | undefined;
    if (shouldCallPrimary && this.primary) {
      try {
        generated = parseGeneratedSummary(await this.primary.summarize(modelInput));
        source = "OPENAI";
      } catch (error) {
        modelFailure = safeFailure(error);
        generated = parseGeneratedSummary(await this.fallback.summarize(modelInput));
      }
    } else {
      generated = parseGeneratedSummary(await this.fallback.summarize(modelInput));
    }

    const priority = source === "OPENAI"
      ? mergeModelPriority(lead.priority.assessment, generated)
      : lead.priority.assessment;
    return {
      ...generated,
      priorityScore: priority.score,
      priorityLevel: priority.level,
      priorityReason: priority.reason,
      source,
      eventIds: ranked.slice(0, 5).map(({ event }) => event.eventId),
      safety: {
        containedUntrustedInstructions: malicious,
        ...(modelFailure ? { modelFailure } : {}),
      },
    };
  }

  async draftReply(request: DraftReplyRequest): Promise<DraftReplyResult> {
    const identity = this.aliases.resolveEvent(request.event);
    const related = [request.event, ...(request.relatedEvents ?? [])]
      .filter((event, index, values) => values.findIndex((candidate) => candidate.eventId === event.eventId) === index);
    const modelInput = {
      userRequest: request.userRequest ?? "Draft a concise reply.",
      preferredTone: request.preferredTone ?? "DIRECT" as const,
      ...(request.userDraftText ? { userDraftText: request.userDraftText } : {}),
      trustedContext: buildBoundedPersonContext(related, identity, undefined, this.aliases),
      notification: wrapUntrustedNotification(request.event),
    };
    const malicious = containsUntrustedInstruction(request.event.body);
    let source: DraftReplyResult["source"] = "DETERMINISTIC_FALLBACK";
    let modelFailure: string | undefined;
    let generated;

    if (this.primary && !malicious) {
      try {
        generated = parseGeneratedDraftReply(await this.primary.draftReply(modelInput));
        source = "OPENAI";
      } catch (error) {
        modelFailure = safeFailure(error);
        generated = parseGeneratedDraftReply(await this.fallback.draftReply(modelInput));
      }
    } else {
      generated = parseGeneratedDraftReply(await this.fallback.draftReply(modelInput));
    }

    return {
      ...generated,
      source,
      eventId: request.event.eventId,
      safety: {
        containedUntrustedInstructions: malicious,
        ...(modelFailure ? { modelFailure } : {}),
      },
    };
  }
}

export function createIntelligenceService(
  openAI: OpenAIResponsesAdapterOptions = {},
): IntelligenceService {
  const apiKey = openAI.apiKey?.trim();
  return new IntelligenceService({
    ...(apiKey ? { primary: new OpenAIResponsesAdapter({ ...openAI, apiKey }) } : {}),
  });
}

function shouldUseModel(
  events: readonly NotificationEvent[],
  priorityScore: number,
  allPromotional: boolean,
  forceModel: boolean,
): boolean {
  if (forceModel) return true;
  if (allPromotional) return false;
  const sources = new Set(events.map((event) => event.app.packageName));
  return priorityScore >= 0.6 || sources.size > 1;
}

function samePerson(
  event: NotificationEvent,
  identity: IdentityResolution,
  aliases: PersonAliasResolver,
): boolean {
  if (identity.kind === "AMBIGUOUS") return false;
  const candidate = aliases.resolveEvent(event);
  return candidate.kind !== "AMBIGUOUS" && candidate.personId === identity.personId;
}

function safeFailure(error: unknown): string {
  if (!(error instanceof Error)) return "UNKNOWN_MODEL_FAILURE";
  return `${error.name}: ${error.message}`.slice(0, 240);
}
