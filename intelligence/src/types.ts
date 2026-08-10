import type {
  AttentionAssessment,
  AttentionLevel,
  NotificationEvent,
} from "../../shared/contracts/domain.js";

export type { AttentionAssessment, AttentionLevel, NotificationEvent };

export type IntelligenceSource = "OPENAI" | "DETERMINISTIC_FALLBACK";

export type IntelligenceIntent =
  | "INFORMATION"
  | "QUESTION"
  | "REQUEST"
  | "REQUEST_FOR_IMMEDIATE_ACTION"
  | "PROMOTION"
  | "UNKNOWN";

export type ReplyTone = "DIRECT" | "WARM" | "NEUTRAL" | "FORMAL";

export interface PersonAlias {
  value: string;
  packageName?: string;
}

export interface PersonAliasSeed {
  personId: string;
  displayName: string;
  importance: number;
  relationship?: string;
  aliases: PersonAlias[];
}

export interface ResolvedIdentity {
  kind: "RESOLVED";
  personId: string;
  displayName: string;
  importance: number;
  relationship?: string;
  matchedAlias: string;
}

export interface AmbiguousIdentity {
  kind: "AMBIGUOUS";
  query: string;
  candidates: Array<{
    personId: string;
    displayName: string;
  }>;
}

export interface ProvisionalIdentity {
  kind: "PROVISIONAL";
  personId: string;
  displayName: string;
  importance: number;
  matchedAlias: string;
}

export type IdentityResolution =
  | ResolvedIdentity
  | AmbiguousIdentity
  | ProvisionalIdentity;

export interface PriorityFeatures {
  personImportance: number;
  urgencySignals: number;
  directRequest: number;
  recency: number;
  sourceRelevance: number;
  promotional: boolean;
  maliciousInstruction: boolean;
}

export interface PriorityResult {
  assessment: AttentionAssessment;
  features: PriorityFeatures;
}

export interface BoundedContextEvent {
  eventId: string;
  appLabel: string;
  packageName: string;
  senderName: string;
  body: string;
  postedAt: string;
  version: number;
}

export interface PersonContext {
  personId: string;
  displayName: string;
  relationship?: string;
  sourceApplications: string[];
  activeEvents: BoundedContextEvent[];
  openRequests: string[];
  truncated: boolean;
}

export interface GeneratedSummary {
  summary: string;
  priorityScore: number;
  priorityLevel: AttentionLevel;
  priorityReason: string;
  intent: IntelligenceIntent;
  requiresResponse: boolean;
  ambiguities: string[];
}

export interface GeneratedDraftReply {
  text: string;
  tone: ReplyTone;
  confidence: number;
  ambiguities: string[];
}

export interface UntrustedNotification {
  classification: "UNTRUSTED_NOTIFICATION_DATA";
  eventId: string;
  appLabel: string;
  senderName: string;
  body: string;
}

export interface SummaryModelInput {
  userRequest: string;
  trustedContext: PersonContext | null;
  deterministicPriority: AttentionAssessment;
  notifications: UntrustedNotification[];
}

export interface DraftReplyModelInput {
  userRequest: string;
  preferredTone: ReplyTone;
  userDraftText?: string;
  trustedContext: PersonContext | null;
  notification: UntrustedNotification;
}

export interface IntelligencePort {
  summarize(input: SummaryModelInput): Promise<GeneratedSummary>;
  draftReply(input: DraftReplyModelInput): Promise<GeneratedDraftReply>;
}

export interface SummaryRequest {
  events: readonly NotificationEvent[];
  now?: string;
  userRequest?: string;
  forceModel?: boolean;
}

export interface DraftReplyRequest {
  event: NotificationEvent;
  relatedEvents?: readonly NotificationEvent[];
  now?: string;
  userRequest?: string;
  userDraftText?: string;
  preferredTone?: ReplyTone;
}

export interface SummaryResult extends GeneratedSummary {
  source: IntelligenceSource;
  eventIds: string[];
  safety: {
    containedUntrustedInstructions: boolean;
    modelFailure?: string;
  };
}

export interface DraftReplyResult extends GeneratedDraftReply {
  source: IntelligenceSource;
  eventId: string;
  safety: {
    containedUntrustedInstructions: boolean;
    modelFailure?: string;
  };
}
