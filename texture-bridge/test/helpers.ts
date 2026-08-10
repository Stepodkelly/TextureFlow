import { FixtureAdapter } from "../src/adapters/fixture.js";
import { silentLogger } from "../src/logger.js";
import { BridgeService, type TraceIdSource } from "../src/service.js";
import { SessionStore } from "../src/session.js";

export const FIXED_NOW = new Date("2026-08-09T18:10:00.000Z");

export function fixedClock(): Date {
  return new Date(FIXED_NOW);
}

export function sequentialTraceIds(): TraceIdSource {
  let sequence = 0;
  return {
    next: () => `trace_test_${++sequence}`
  };
}

export function createFixtureService(adapter = new FixtureAdapter({ now: fixedClock })) {
  return new BridgeService({
    backend: adapter,
    ownerId: "test-owner",
    sessions: new SessionStore({ now: () => FIXED_NOW.getTime() }),
    logger: silentLogger,
    traceIds: sequentialTraceIds()
  });
}

export const context = {
  ownerId: "test-owner",
  sessionId: "test-session",
  traceId: "trace_test"
};
