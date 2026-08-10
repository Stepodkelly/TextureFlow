import type {
  AttentionAssessment,
  AttentionLevel,
  IdentityResolution,
  NotificationEvent,
  PriorityFeatures,
  PriorityResult,
} from "./types.js";

const PROMOTION_PATTERN = /\b(?:sale|discount|coupon|promo(?:tion)?|limited offer|% off|shop now|unsubscribe|deal ends|free shipping)\b/i;
const MALICIOUS_INSTRUCTION_PATTERN = /(?:ignore (?:all |any )?(?:previous|prior|system)|system prompt|developer message|call (?:the )?(?:tool|function)|confirm[_ ]action|execute (?:the )?command|mark (?:it )?as dispatched|reveal (?:the )?(?:secret|api key))/i;
const URGENT_PATTERN = /\b(?:urgent|asap|emergency|immediately|right now|locked out|door is locked|downstairs|waiting outside|hospital|help me|deadline today|due today)\b/i;
const STRONG_REQUEST_PATTERN = /\b(?:can you|could you|would you|please|need you to|will you|are we|what time|when will|where are|let me know|reply|call me)\b/i;
const REQUEST_PATTERN = /\?|\b(?:need|want|send|bring|tell|confirm|check)\b/i;

export function containsUntrustedInstruction(body: string | undefined): boolean {
  return body ? MALICIOUS_INSTRUCTION_PATTERN.test(body) : false;
}

export function isPromotional(body: string | undefined): boolean {
  return body ? PROMOTION_PATTERN.test(body) : false;
}

export function assessPriority(
  event: NotificationEvent,
  identity: IdentityResolution,
  now = new Date(),
): PriorityResult {
  const body = event.body ?? "";
  const maliciousInstruction = containsUntrustedInstruction(body);
  const promotional = isPromotional(body);
  const personImportance = identity.kind === "AMBIGUOUS" ? 0.5 : identity.importance;
  const urgencySignals = maliciousInstruction ? 0 : urgencyStrength(body);
  const directRequest = maliciousInstruction ? 0 : requestStrength(body);
  const recency = recencyStrength(event.postedAt, now);
  const sourceRelevance = sourceStrength(event.app.packageName);

  const features: PriorityFeatures = {
    personImportance,
    urgencySignals,
    directRequest,
    recency,
    sourceRelevance,
    promotional,
    maliciousInstruction,
  };

  let score =
    0.3 * personImportance
    + 0.25 * urgencySignals
    + 0.2 * directRequest
    + 0.15 * recency
    + 0.1 * sourceRelevance;

  if (promotional && urgencySignals === 0 && directRequest === 0) {
    score *= 0.25;
  }
  if (maliciousInstruction) {
    score = Math.min(score, 0.49);
  }

  score = roundScore(score);
  return {
    assessment: {
      score,
      level: levelForScore(score),
      reason: priorityReason(features),
    },
    features,
  };
}

export function levelForScore(score: number): AttentionLevel {
  if (score >= 0.8) return "URGENT";
  if (score >= 0.6) return "IMPORTANT";
  if (score >= 0.35) return "NORMAL";
  return "LOW";
}

export function mergeModelPriority(
  deterministic: AttentionAssessment,
  model: Pick<
    import("./types.js").GeneratedSummary,
    "priorityScore" | "priorityReason"
  >,
): AttentionAssessment {
  if (deterministic.score < 0.35 || deterministic.score >= 0.75) {
    return deterministic;
  }
  const boundedSuggestion = Math.max(
    deterministic.score - 0.1,
    Math.min(deterministic.score + 0.1, model.priorityScore),
  );
  const score = roundScore((deterministic.score * 2 + boundedSuggestion) / 3);
  return {
    score,
    level: levelForScore(score),
    reason: `${deterministic.reason} Model evidence: ${model.priorityReason}`,
  };
}

function urgencyStrength(body: string): number {
  if (!URGENT_PATTERN.test(body)) return 0;
  if (/\b(?:emergency|hospital|help me|locked out|door is locked|waiting outside)\b/i.test(body)) {
    return 1;
  }
  return 0.8;
}

function requestStrength(body: string): number {
  if (STRONG_REQUEST_PATTERN.test(body)) return 1;
  if (REQUEST_PATTERN.test(body)) return 0.75;
  if (/\b(?:locked|downstairs|waiting)\b/i.test(body)) return 0.8;
  return 0;
}

function recencyStrength(postedAt: string, now: Date): number {
  const posted = Date.parse(postedAt);
  if (!Number.isFinite(posted)) return 0.3;
  const ageMinutes = Math.max(0, (now.getTime() - posted) / 60_000);
  if (ageMinutes <= 15) return 1;
  if (ageMinutes <= 60) return 0.85;
  if (ageMinutes <= 240) return 0.65;
  if (ageMinutes <= 1_440) return 0.4;
  return 0.15;
}

function sourceStrength(packageName: string): number {
  if (/whatsapp|telegram|messag|sms/i.test(packageName)) return 0.9;
  if (/mail|slack|teams/i.test(packageName)) return 0.7;
  return 0.5;
}

function priorityReason(features: PriorityFeatures): string {
  if (features.maliciousInstruction) {
    return "The notification contains untrusted instruction-like content and needs review.";
  }
  if (features.promotional) {
    return "Promotional language was detected without a direct or urgent request.";
  }
  if (features.urgencySignals >= 0.8 && features.directRequest >= 0.75) {
    return "A recent, direct request contains immediate timing or access signals.";
  }
  if (features.urgencySignals >= 0.8) {
    return "The message contains a recent, immediate timing or safety signal.";
  }
  if (features.directRequest >= 0.75) {
    return "A recent direct question or request likely needs a response.";
  }
  if (features.personImportance >= 0.8) {
    return "A recent message came from an important contact.";
  }
  return "A recent notification has no strong urgency or request signal.";
}

function roundScore(value: number): number {
  return Math.round(Math.max(0, Math.min(1, value)) * 100) / 100;
}
