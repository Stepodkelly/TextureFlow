import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import {
  DRAFT_REPLY_JSON_SCHEMA,
  IntelligenceService,
  IntelligenceUnavailableError,
  OpenAIResponsesAdapter,
  PersonAliasResolver,
  SUMMARY_JSON_SCHEMA,
  assessPriority,
  buildBoundedPersonContext,
  createIntelligenceService,
  parseGeneratedDraftReply,
  parseGeneratedSummary,
  wrapUntrustedNotification,
  type DraftReplyModelInput,
  type GeneratedDraftReply,
  type GeneratedSummary,
  type IntelligencePort,
  type NotificationEvent,
  type PersonAliasSeed,
  type SummaryModelInput,
} from "../src/index.js";

interface EvalCase {
  id: string;
  category: string;
  now?: string;
  query?: string;
  aliasSeeds?: PersonAliasSeed[];
  events?: NotificationEvent[];
  expected: Record<string, unknown>;
}

const evalCases = JSON.parse(
  readFileSync(resolve(process.cwd(), "../shared/evals/intelligence-cases.json"), "utf8"),
) as EvalCase[];

function evalCase(category: string): EvalCase {
  const fixture = evalCases.find((candidate) => candidate.category === category);
  assert.ok(fixture, `Missing ${category} eval fixture.`);
  return fixture;
}

function eventsFor(fixture: EvalCase): NotificationEvent[] {
  assert.ok(fixture.events, `${fixture.id} must include events.`);
  return fixture.events;
}

test("malicious notification content remains untrusted data", async () => {
  const fixture = evalCase("malicious-content");
  const result = await new IntelligenceService().summarize({
    events: eventsFor(fixture),
    ...(fixture.now ? { now: fixture.now } : {}),
  });

  assert.equal(result.safety.containedUntrustedInstructions, true);
  assert.ok(result.priorityScore <= Number(fixture.expected.maximumPriority));
  for (const term of fixture.expected.forbiddenSummaryTerms as string[]) {
    assert.equal(result.summary.toLowerCase().includes(term.toLowerCase()), false);
  }
  assert.equal(result.source, "DETERMINISTIC_FALLBACK");
});

test("ambiguous aliases are never silently merged", () => {
  const fixture = evalCase("ambiguity");
  assert.ok(fixture.aliasSeeds);
  assert.ok(fixture.query);
  const resolution = new PersonAliasResolver(fixture.aliasSeeds).resolveMention(fixture.query);

  assert.equal(resolution.kind, fixture.expected.resolution);
  assert.equal(resolution.kind === "AMBIGUOUS" ? resolution.candidates.length : 0, fixture.expected.candidateCount);
});

test("locked-out direct contact scores as urgent", async () => {
  const fixture = evalCase("urgency");
  const events = eventsFor(fixture);
  const event = events[0]!;
  assert.ok(fixture.now);
  const identity = new PersonAliasResolver().resolveEvent(event);
  const priority = assessPriority(event, identity, new Date(fixture.now));
  const summary = await new IntelligenceService().summarize({
    events,
    ...(fixture.now ? { now: fixture.now } : {}),
  });

  assert.ok(priority.assessment.score >= Number(fixture.expected.minimumPriority));
  assert.equal(priority.assessment.level, fixture.expected.level);
  assert.equal(summary.requiresResponse, fixture.expected.requiresResponse);
});

test("promotions remain low priority and do not spend a model call", async () => {
  const fixture = evalCase("promotional");
  const primary = new CountingPort();
  const result = await new IntelligenceService({ primary }).summarize({
    events: eventsFor(fixture),
    ...(fixture.now ? { now: fixture.now } : {}),
  });

  assert.ok(result.priorityScore <= Number(fixture.expected.maximumPriority));
  assert.equal(result.intent, fixture.expected.intent);
  assert.equal(primary.summaryCalls, fixture.expected.modelCalls);
  assert.equal(result.source, "DETERMINISTIC_FALLBACK");
});

test("bounded context joins deterministic aliases across applications", () => {
  const fixture = evalCase("cross-app-context");
  const events = eventsFor(fixture);
  const identity = new PersonAliasResolver().resolveEvent(events[0]!);
  const context = buildBoundedPersonContext(events, identity);

  assert.ok(context);
  assert.equal(context.personId, fixture.expected.personId);
  assert.equal(context.sourceApplications.length, fixture.expected.sourceCount);
  assert.equal(context.activeEvents.length, fixture.expected.eventCount);
});

test("context is bounded to five events and a finite character budget", () => {
  const source = eventsFor(evalCase("urgency"))[0]!;
  const events = Array.from({ length: 8 }, (_, index): NotificationEvent => ({
    ...source,
    eventId: `bounded_${index}`,
    body: `${index}:${"x".repeat(900)}`,
    postedAt: new Date(Date.parse(source.postedAt) - index * 60_000).toISOString(),
    updatedAt: new Date(Date.parse(source.updatedAt) - index * 60_000).toISOString(),
  }));
  const identity = new PersonAliasResolver().resolveEvent(source);
  const context = buildBoundedPersonContext(events, identity);

  assert.ok(context);
  assert.ok(context.activeEvents.length <= 5);
  assert.ok(context.activeEvents.reduce((total, event) => total + event.body.length, 0) <= 2_000);
  assert.equal(context.truncated, true);
});

test("strict validators reject missing, unknown, and out-of-range fields", () => {
  const validSummary = generatedSummary();
  assert.deepEqual(parseGeneratedSummary(validSummary), validSummary);
  assert.throws(
    () => parseGeneratedSummary({ ...validSummary, command: { type: "REPLY" } }),
    /unknown keys/i,
  );
  assert.throws(
    () => parseGeneratedSummary({ ...validSummary, priorityScore: 1.5 }),
    /0 to 1/i,
  );

  const validDraft = generatedDraft();
  assert.deepEqual(parseGeneratedDraftReply(validDraft), validDraft);
  const { confidence: _confidence, ...missingConfidence } = validDraft;
  assert.throws(() => parseGeneratedDraftReply(missingConfidence), /missing keys/i);
  assert.equal(SUMMARY_JSON_SCHEMA.additionalProperties, false);
  assert.equal(DRAFT_REPLY_JSON_SCHEMA.additionalProperties, false);
});

test("Responses adapter requests strict structured output and parses it", async () => {
  let requestBody: Record<string, unknown> | undefined;
  const mockFetch = (async (_input: string | URL | Request, init?: RequestInit) => {
    requestBody = JSON.parse(String(init?.body)) as Record<string, unknown>;
    return new Response(JSON.stringify({ output_text: JSON.stringify(generatedSummary()) }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;
  const adapter = new OpenAIResponsesAdapter({
    apiKey: "test-only-key",
    fetchImplementation: mockFetch,
  });
  const event = eventsFor(evalCase("urgency"))[0]!;
  const result = await adapter.summarize({
    userRequest: "What needs attention?",
    trustedContext: null,
    deterministicPriority: event.priority,
    notifications: [wrapUntrustedNotification(event)],
  });

  assert.deepEqual(result, generatedSummary());
  assert.equal(requestBody?.store, false);
  const text = requestBody?.text as { format: Record<string, unknown> };
  assert.equal(text.format.type, "json_schema");
  assert.equal(text.format.strict, true);
  assert.equal((text.format.schema as { additionalProperties: boolean }).additionalProperties, false);
  assert.match(JSON.stringify(requestBody?.input), /UNTRUSTED_NOTIFICATION_DATA/);
});

test("invalid model fields fail closed into deterministic fallback", async () => {
  const invalidPrimary: IntelligencePort = {
    async summarize() {
      return { ...generatedSummary(), command: "SEND_NOW" } as GeneratedSummary;
    },
    async draftReply() {
      return generatedDraft();
    },
  };
  const fixture = evalCase("urgency");
  const result = await new IntelligenceService({ primary: invalidPrimary }).summarize({
    events: eventsFor(fixture),
    ...(fixture.now ? { now: fixture.now } : {}),
  });

  assert.equal(result.source, "DETERMINISTIC_FALLBACK");
  assert.match(result.safety.modelFailure ?? "", /unknown keys/i);
});

test("missing API key and network failures have deterministic fallback", async () => {
  const fixture = evalCase("urgency");
  const event = eventsFor(fixture)[0]!;
  const noKeyAdapter = new OpenAIResponsesAdapter();
  await assert.rejects(
    noKeyAdapter.summarize({
      userRequest: "Summarize",
      trustedContext: null,
      deterministicPriority: event.priority,
      notifications: [wrapUntrustedNotification(event)],
    }),
    (error: unknown) => error instanceof IntelligenceUnavailableError && error.code === "NO_API_KEY",
  );

  const noKeyService = createIntelligenceService({});
  const local = await noKeyService.summarize({
    events: [event],
    ...(fixture.now ? { now: fixture.now } : {}),
  });
  assert.equal(local.source, "DETERMINISTIC_FALLBACK");

  const failingFetch = (async () => {
    throw new Error("offline");
  }) as typeof fetch;
  const networkService = new IntelligenceService({
    primary: new OpenAIResponsesAdapter({
      apiKey: "test-only-key",
      fetchImplementation: failingFetch,
    }),
  });
  const networkResult = await networkService.summarize({
    events: [event],
    ...(fixture.now ? { now: fixture.now } : {}),
  });
  assert.equal(networkResult.source, "DETERMINISTIC_FALLBACK");
  assert.match(networkResult.safety.modelFailure ?? "", /offline/i);
});

test("drafting returns text but exposes no authorization surface", async () => {
  const event = eventsFor(evalCase("urgency"))[0]!;
  const result = await new IntelligenceService().draftReply({
    event,
    userDraftText: "I'm coming downstairs now.",
    preferredTone: "DIRECT",
  });

  assert.equal(result.text, "I'm coming downstairs now.");
  assert.equal(result.source, "DETERMINISTIC_FALLBACK");
  assert.deepEqual(Object.keys(result).sort(), [
    "ambiguities",
    "confidence",
    "eventId",
    "safety",
    "source",
    "text",
    "tone",
  ]);
});

class CountingPort implements IntelligencePort {
  summaryCalls = 0;
  draftCalls = 0;

  async summarize(_input: SummaryModelInput): Promise<GeneratedSummary> {
    this.summaryCalls += 1;
    return generatedSummary();
  }

  async draftReply(_input: DraftReplyModelInput): Promise<GeneratedDraftReply> {
    this.draftCalls += 1;
    return generatedDraft();
  }
}

function generatedSummary(): GeneratedSummary {
  return {
    summary: "Sam is downstairs and cannot enter.",
    priorityScore: 0.94,
    priorityLevel: "URGENT",
    priorityReason: "A close contact is waiting outside.",
    intent: "REQUEST_FOR_IMMEDIATE_ACTION",
    requiresResponse: true,
    ambiguities: [],
  };
}

function generatedDraft(): GeneratedDraftReply {
  return {
    text: "I'm coming downstairs now.",
    tone: "DIRECT",
    confidence: 0.91,
    ambiguities: [],
  };
}
