#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createBackend, readConfig } from "./config.js";
import { StderrLogger } from "./logger.js";
import { createMcpServer } from "./server.js";
import { BridgeService } from "./service.js";
import { SessionStore } from "./session.js";

async function main(): Promise<void> {
  const config = readConfig();
  const logger = new StderrLogger();
  const service = new BridgeService({
    backend: createBackend(config),
    ownerId: config.ownerId,
    sessions: new SessionStore({ ttlMs: config.sessionTtlMs }),
    logger
  });
  const server = createMcpServer(service);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  logger.log("info", "bridge_started", { adapter: config.adapter });
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "Unknown startup error";
  console.error(JSON.stringify({
    timestamp: new Date().toISOString(),
    level: "error",
    event: "bridge_start_failed",
    message
  }));
  process.exitCode = 1;
});
