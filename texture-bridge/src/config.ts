import { z } from "zod";
import { ConvexAdapter, HttpConvexCaller } from "./adapters/convex.js";
import type { TextureBackend } from "./adapters/backend.js";
import { FixtureAdapter } from "./adapters/fixture.js";

const ConfigSchema = z.discriminatedUnion("adapter", [
  z.object({
    adapter: z.literal("fixture"),
    ownerId: z.string().min(1),
    sessionTtlMs: z.number().int().positive()
  }),
  z.object({
    adapter: z.literal("convex"),
    ownerId: z.string().min(1),
    sessionTtlMs: z.number().int().positive(),
    convexUrl: z.string().url(),
    convexAuthToken: z.string().min(1).optional(),
    bridgeToken: z.string().min(1).optional(),
    receiptTimeoutMs: z.number().int().positive()
  })
]);

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
  const adapter = z.enum(["fixture", "convex"]).parse(
    environment.TEXTUREFLOW_ADAPTER ?? "fixture"
  );
  const common = {
    adapter,
    ownerId: environment.TEXTUREFLOW_OWNER_ID ?? "demo-owner",
    sessionTtlMs: positiveInteger(environment.TEXTUREFLOW_SESSION_TTL_MS, 120_000)
  };

  if (adapter === "fixture") {
    return ConfigSchema.parse(common);
  }

  return ConfigSchema.parse({
    ...common,
    convexUrl: environment.CONVEX_URL,
    convexAuthToken: environment.CONVEX_AUTH_TOKEN,
    bridgeToken: environment.TEXTUREFLOW_BRIDGE_TOKEN,
    receiptTimeoutMs: positiveInteger(environment.TEXTUREFLOW_RECEIPT_TIMEOUT_MS, 15_000)
  });
}

export function createBackend(config: BridgeConfig): TextureBackend {
  if (config.adapter === "fixture") {
    return new FixtureAdapter();
  }
  return new ConvexAdapter(
    new HttpConvexCaller(config.convexUrl, config.convexAuthToken),
    {
      ...(config.bridgeToken ? { bridgeToken: config.bridgeToken } : {}),
      receiptTimeoutMs: config.receiptTimeoutMs
    }
  );
}
