import { describe, expect, it } from "vitest";
import { buildFixtureEvents } from "../src/fixtures/data.js";
import { SessionStore } from "../src/session.js";
import { FIXED_NOW } from "./helpers.js";

describe("SessionStore", () => {
  it("keeps conversational references scoped to one short-lived session", () => {
    let now = FIXED_NOW.getTime();
    const store = new SessionStore({ now: () => now, ttlMs: 1_000 });
    const event = buildFixtureEvents(FIXED_NOW)[0];
    expect(event).toBeDefined();

    store.recordEvents("session-a", [event!]);
    expect(store.resolveEvent("session-a", "that one")).toBe(event!.eventId);
    expect(() => store.resolveEvent("session-b", "that one")).toThrow(
      /cannot tell which notification/i
    );

    now += 1_001;
    expect(() => store.resolveEvent("session-a", "that one")).toThrow(
      /cannot tell which notification/i
    );
  });

  it("accepts explicit stable IDs without inventing a session reference", () => {
    const store = new SessionStore();
    expect(store.resolveEvent("new-session", "evt_known_123")).toBe("evt_known_123");
    expect(store.resolveEvent("new-session", "k17a3bc9z0")).toBe("k17a3bc9z0");
  });
});
