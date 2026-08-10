import { describe, expect, it } from "vitest";
import { readConfig } from "../src/config.js";

describe("bridge configuration", () => {
  it("defaults explicitly to labeled fixture mode", () => {
    expect(readConfig({})).toMatchObject({
      adapter: "fixture",
      ownerId: "demo-owner"
    });
  });

  it("fails closed on an unknown adapter instead of silently entering rehearsal", () => {
    expect(() => readConfig({ TEXTUREFLOW_ADAPTER: "convxe" })).toThrow();
  });

  it("requires a deployment URL for live Convex mode", () => {
    expect(() => readConfig({ TEXTUREFLOW_ADAPTER: "convex" })).toThrow();
  });
});
