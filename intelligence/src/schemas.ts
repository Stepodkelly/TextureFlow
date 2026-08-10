import type {
  GeneratedDraftReply,
  GeneratedSummary,
  IntelligenceIntent,
  ReplyTone,
} from "./types.js";

export const SUMMARY_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    summary: { type: "string", minLength: 1, maxLength: 280 },
    priorityScore: { type: "number", minimum: 0, maximum: 1 },
    priorityLevel: {
      type: "string",
      enum: ["LOW", "NORMAL", "IMPORTANT", "URGENT"],
    },
    priorityReason: { type: "string", minLength: 1, maxLength: 220 },
    intent: {
      type: "string",
      enum: [
        "INFORMATION",
        "QUESTION",
        "REQUEST",
        "REQUEST_FOR_IMMEDIATE_ACTION",
        "PROMOTION",
        "UNKNOWN",
      ],
    },
    requiresResponse: { type: "boolean" },
    ambiguities: {
      type: "array",
      maxItems: 3,
      items: { type: "string", minLength: 1, maxLength: 120 },
    },
  },
  required: [
    "summary",
    "priorityScore",
    "priorityLevel",
    "priorityReason",
    "intent",
    "requiresResponse",
    "ambiguities",
  ],
} as const;

export const DRAFT_REPLY_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    text: { type: "string", minLength: 1, maxLength: 500 },
    tone: {
      type: "string",
      enum: ["DIRECT", "WARM", "NEUTRAL", "FORMAL"],
    },
    confidence: { type: "number", minimum: 0, maximum: 1 },
    ambiguities: {
      type: "array",
      maxItems: 3,
      items: { type: "string", minLength: 1, maxLength: 120 },
    },
  },
  required: ["text", "tone", "confidence", "ambiguities"],
} as const;

const SUMMARY_KEYS = [
  "summary",
  "priorityScore",
  "priorityLevel",
  "priorityReason",
  "intent",
  "requiresResponse",
  "ambiguities",
] as const;

const DRAFT_KEYS = ["text", "tone", "confidence", "ambiguities"] as const;
const INTENTS: readonly IntelligenceIntent[] = [
  "INFORMATION",
  "QUESTION",
  "REQUEST",
  "REQUEST_FOR_IMMEDIATE_ACTION",
  "PROMOTION",
  "UNKNOWN",
];
const TONES: readonly ReplyTone[] = ["DIRECT", "WARM", "NEUTRAL", "FORMAL"];
const LEVELS = ["LOW", "NORMAL", "IMPORTANT", "URGENT"] as const;

export class IntelligenceSchemaError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "IntelligenceSchemaError";
  }
}

export function parseGeneratedSummary(value: unknown): GeneratedSummary {
  const record = exactRecord(value, SUMMARY_KEYS, "summary result");
  return {
    summary: boundedString(record.summary, "summary", 280),
    priorityScore: boundedNumber(record.priorityScore, "priorityScore"),
    priorityLevel: enumValue(record.priorityLevel, LEVELS, "priorityLevel"),
    priorityReason: boundedString(record.priorityReason, "priorityReason", 220),
    intent: enumValue(record.intent, INTENTS, "intent"),
    requiresResponse: booleanValue(record.requiresResponse, "requiresResponse"),
    ambiguities: boundedStrings(record.ambiguities, "ambiguities", 3, 120),
  };
}

export function parseGeneratedDraftReply(value: unknown): GeneratedDraftReply {
  const record = exactRecord(value, DRAFT_KEYS, "draft reply result");
  return {
    text: boundedString(record.text, "text", 500),
    tone: enumValue(record.tone, TONES, "tone"),
    confidence: boundedNumber(record.confidence, "confidence"),
    ambiguities: boundedStrings(record.ambiguities, "ambiguities", 3, 120),
  };
}

function exactRecord<const K extends readonly string[]>(
  value: unknown,
  keys: K,
  label: string,
): Record<K[number], unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new IntelligenceSchemaError(`${label} must be an object.`);
  }
  const record = value as Record<string, unknown>;
  const allowed = new Set<string>(keys);
  const unknownKeys = Object.keys(record).filter((key) => !allowed.has(key));
  const missingKeys = keys.filter((key) => !(key in record));
  if (unknownKeys.length > 0) {
    throw new IntelligenceSchemaError(`${label} contains unknown keys: ${unknownKeys.join(", ")}.`);
  }
  if (missingKeys.length > 0) {
    throw new IntelligenceSchemaError(`${label} is missing keys: ${missingKeys.join(", ")}.`);
  }
  return record as Record<K[number], unknown>;
}

function boundedString(value: unknown, field: string, maximum: number): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maximum) {
    throw new IntelligenceSchemaError(`${field} must be a non-empty string of at most ${maximum} characters.`);
  }
  return value.trim();
}

function boundedNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > 1) {
    throw new IntelligenceSchemaError(`${field} must be a finite number from 0 to 1.`);
  }
  return value;
}

function booleanValue(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") {
    throw new IntelligenceSchemaError(`${field} must be a boolean.`);
  }
  return value;
}

function enumValue<const T extends string>(
  value: unknown,
  allowed: readonly T[],
  field: string,
): T {
  if (typeof value !== "string" || !allowed.includes(value as T)) {
    throw new IntelligenceSchemaError(`${field} has an unsupported value.`);
  }
  return value as T;
}

function boundedStrings(
  value: unknown,
  field: string,
  maximumItems: number,
  maximumLength: number,
): string[] {
  if (!Array.isArray(value) || value.length > maximumItems) {
    throw new IntelligenceSchemaError(`${field} must contain at most ${maximumItems} strings.`);
  }
  return value.map((item, index) => boundedString(item, `${field}[${index}]`, maximumLength));
}
