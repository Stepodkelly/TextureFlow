import { z } from "zod";
import type { TextureBackend } from "./adapters/backend.js";
import { FixtureAdapter } from "./adapters/fixture.js";

/**
 * Convex / VoiceOS live adapters are stubbed out of the default path.
 * The bridge always uses the local fixture backend until those sections are deleted or restored.
 */
const ConfigSchema = z.object({
  adapter: z.literal("fixture"),
  ownerId: z.string().min(1),
  sessionTtlMs: z.number().int().positive()
});

export type BridgeConfig = z.infer<typeof ConfigSchema>;

function positiveInteger(value: string | undefined, fallback: number): number {
  if (value === undefined) {
    return fallback;
  }
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`Expected a positive integer, received ${value}.`);
  }
  return parsed;
}

export function readConfig(environment: NodeJS.ProcessEnv = process.env): BridgeConfig {
  const requested = environment.TEXTUREFLOW_ADAPTER ?? "fixture";
  if (requested === "convex") {
    console.warn(
      "[texture-bridge] TEXTUREFLOW_ADAPTER=convex is stubbed; using fixture backend instead."
    );
  }
  return ConfigSchema.parse({
    adapter: "fixture",
    ownerId: environment.TEXTUREFLOW_OWNER_ID ?? "demo-owner",
    sessionTtlMs: positiveInteger(environment.TEXTUREFLOW_SESSION_TTL_MS, 120_000)
  });
}

export function createBackend(config: BridgeConfig): TextureBackend {
  void config;
  return new FixtureAdapter();
}
