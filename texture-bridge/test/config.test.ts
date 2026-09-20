import { describe, expect, it, vi } from "vitest";
import { createBackend, readConfig } from "../src/config.js";

describe("bridge configuration", () => {
  it("defaults to fixture mode", () => {
    expect(readConfig({})).toMatchObject({
      adapter: "fixture",
      ownerId: "demo-owner"
    });
  });

  it("forces fixture even when convex is requested (stubbed)", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    expect(readConfig({ TEXTUREFLOW_ADAPTER: "convex" })).toMatchObject({
      adapter: "fixture"
    });
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it("createBackend always returns the fixture adapter", () => {
    const backend = createBackend(readConfig({}));
    expect(backend.constructor.name).toBe("FixtureAdapter");
  });
});
