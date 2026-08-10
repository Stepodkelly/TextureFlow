import { cleanText } from "./context.js";
import {
  containsUntrustedInstruction,
  isPromotional,
  levelForScore,
} from "./priority.js";
import type {
  DraftReplyModelInput,
  GeneratedDraftReply,
  GeneratedSummary,
  IntelligenceIntent,
  IntelligencePort,
  SummaryModelInput,
} from "./types.js";

export class DeterministicIntelligence implements IntelligencePort {
  async summarize(input: SummaryModelInput): Promise<GeneratedSummary> {
    const first = input.notifications[0];
    const malicious = input.notifications.some((item) => containsUntrustedInstruction(item.body));
    const promotional = input.notifications.every((item) => isPromotional(item.body));
    const intent = inferIntent(input.notifications.map((item) => item.body), malicious, promotional);
    const sender = input.trustedContext?.displayName ?? first?.senderName ?? "An unknown sender";
    const summary = malicious
      ? `${sender} sent a notification containing untrusted instruction-like content. Review it directly.`
      : deterministicSummary(sender, input.notifications.map((item) => item.body), promotional);

    return {
      summary,
      priorityScore: input.deterministicPriority.score,
      priorityLevel: levelForScore(input.deterministicPriority.score),
      priorityReason: input.deterministicPriority.reason,
      intent,
      requiresResponse: !malicious && !promotional && isResponseLikely(input.notifications.map((item) => item.body)),
      ambiguities: input.trustedContext ? [] : ["The sender identity is unresolved or ambiguous."],
    };
  }

  async draftReply(input: DraftReplyModelInput): Promise<GeneratedDraftReply> {
    const malicious = containsUntrustedInstruction(input.notification.body);
    const explicitDraft = cleanText(input.userDraftText ?? "");
    const text = explicitDraft
      ? truncate(explicitDraft, 500)
      : malicious
        ? "Thanks for the message. I will review it directly."
        : fallbackReply(input.notification.body, input.preferredTone);

    return {
      text,
      tone: input.preferredTone,
      confidence: explicitDraft ? 1 : malicious ? 0.35 : 0.55,
      ambiguities: input.trustedContext ? [] : ["Confirm which person this reply is for."],
    };
  }
}

function inferIntent(
  bodies: readonly string[],
  malicious: boolean,
  promotional: boolean,
): IntelligenceIntent {
  if (malicious) return "UNKNOWN";
  if (promotional) return "PROMOTION";
  const joined = bodies.join(" ");
  if (/\b(?:emergency|asap|right now|locked out|door is locked|waiting outside)\b/i.test(joined)) {
    return "REQUEST_FOR_IMMEDIATE_ACTION";
  }
  if (/\?/.test(joined)) return "QUESTION";
  if (/\b(?:please|can you|could you|need|let me know)\b/i.test(joined)) return "REQUEST";
  return joined.trim() ? "INFORMATION" : "UNKNOWN";
}

function deterministicSummary(
  sender: string,
  bodies: readonly string[],
  promotional: boolean,
): string {
  if (promotional) return `${sender} sent a promotional notification.`;
  const usable = bodies.map(cleanText).filter(Boolean).slice(0, 2);
  if (usable.length === 0) return `${sender} sent a notification without readable message text.`;
  const joined = truncate(usable.join(" "), 210);
  return `${sender}: ${joined}`;
}

function fallbackReply(body: string, tone: DraftReplyModelInput["preferredTone"]): string {
  if (/\?/.test(body)) {
    return tone === "FORMAL"
      ? "Thank you for checking. I will confirm shortly."
      : "Thanks for checking. I'll confirm shortly.";
  }
  return tone === "WARM"
    ? "Thanks for letting me know. I appreciate it."
    : "Thanks for letting me know.";
}

function isResponseLikely(bodies: readonly string[]): boolean {
  return bodies.some((body) => /\?|\b(?:please|can you|could you|need|let me know|locked|waiting)\b/i.test(body));
}

function truncate(value: string, maximum: number): string {
  return value.length <= maximum ? value : `${value.slice(0, maximum - 3).trimEnd()}...`;
}
