import type { MutationCtx } from "../_generated/server";

const FORBIDDEN_METADATA_KEYS = new Set([
  "body",
  "content",
  "message",
  "messageBody",
  "notificationText",
  "payload",
]);

export function sanitizeTraceMetadata(
  metadata: Record<string, string | number | boolean> | undefined,
): Record<string, string | number | boolean> | undefined {
  if (!metadata) {
    return undefined;
  }
  const clean: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(metadata)) {
    if (FORBIDDEN_METADATA_KEYS.has(key)) {
      throw new Error(`Trace metadata cannot contain sensitive key: ${key}`);
    }
    if (typeof value === "string" && value.length > 256) {
      throw new Error(`Trace metadata value for ${key} exceeds 256 characters.`);
    }
    clean[key] = value;
  }
  return clean;
}

export async function appendTrace(
  ctx: MutationCtx,
  input: {
    ownerId: string;
    mode: "LIVE" | "REHEARSAL";
    traceId: string;
    spanId?: string;
    parentSpanId?: string;
    sequence?: number;
    name: string;
    service: "ANDROID_MOBILE" | "TEXTUREFLOW_CORE" | "TEXTUREFLOW_BRIDGE" | "TEXTURE_ENGINE" | "INTELLIGENCE" | "QA_HARNESS";
    outcome?: "OK" | "ERROR" | "SKIPPED" | "TIMEOUT";
    durationMs?: number;
    correlation?: {
      eventId?: string;
      eventVersion?: number;
      proposalId?: string;
      commandId?: string;
      deviceId?: string;
      sessionId?: string;
    };
    attributes?: Record<string, string | number | boolean>;
    occurredAtMs?: number;
  },
) {
  const occurredAtMs = input.occurredAtMs ?? Date.now();
  const primaryCorrelation =
    input.correlation?.commandId ??
    input.correlation?.proposalId ??
    input.correlation?.eventId ??
    input.correlation?.deviceId ??
    "span";
  return await ctx.db.insert("traceEvents", {
    ownerId: input.ownerId,
    contractVersion: 1,
    mode: input.mode,
    traceId: input.traceId,
    spanId: input.spanId ?? `${input.traceId}:${input.name}:${primaryCorrelation}:${occurredAtMs}`,
    parentSpanId: input.parentSpanId,
    sequence: input.sequence,
    name: input.name,
    service: input.service,
    outcome: input.outcome ?? "OK",
    occurredAt: new Date(occurredAtMs).toISOString(),
    occurredAtMs,
    durationMs: input.durationMs,
    correlation: input.correlation ?? {},
    attributes: sanitizeTraceMetadata(input.attributes) ?? {},
  });
}
