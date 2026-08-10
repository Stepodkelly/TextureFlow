import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import type { Transport } from "@modelcontextprotocol/sdk/shared/transport.js";
import { afterEach, describe, expect, it } from "vitest";
import type { BridgeConfig } from "../src/config.js";
import {
  readHttpListenConfig,
  startHttpBridge,
  type RunningHttpBridge
} from "../src/http.js";
import type { LogLevel, Logger } from "../src/logger.js";
import { createFixtureService } from "./helpers.js";

const fixtureConfig: BridgeConfig = {
  adapter: "fixture",
  ownerId: "http-test-owner",
  sessionTtlMs: 120_000
};

class CapturingLogger implements Logger {
  readonly entries: Array<Record<string, unknown>> = [];

  log(level: LogLevel, event: string, details: Record<string, unknown> = {}): void {
    this.entries.push({ level, event, ...details });
  }
}

const runningBridges: RunningHttpBridge[] = [];

afterEach(async () => {
  await Promise.all(runningBridges.splice(0).map((running) => running.close()));
});

async function startFixtureBridge(options: {
  logger?: Logger;
  serviceFactory?: () => ReturnType<typeof createFixtureService>;
  sessionIdGenerator?: () => string;
} = {}): Promise<RunningHttpBridge> {
  const running = await startHttpBridge({
    config: fixtureConfig,
    host: "127.0.0.1",
    port: 0,
    keepAliveMs: 10,
    ...(options.logger ? { logger: options.logger } : {}),
    ...(options.serviceFactory ? { serviceFactory: options.serviceFactory } : {}),
    ...(options.sessionIdGenerator
      ? { sessionIdGenerator: options.sessionIdGenerator }
      : {})
  });
  runningBridges.push(running);
  return running;
}

function createClient(url: URL, token?: string): {
  client: Client;
  requests: Array<{ method: string; status: number }>;
  transport: StreamableHTTPClientTransport;
} {
  const requests: Array<{ method: string; status: number }> = [];
  const instrumentedFetch: typeof globalThis.fetch = async (input, init) => {
    const method = init?.method ?? (input instanceof Request ? input.method : "GET");
    const response = await globalThis.fetch(input, init);
    requests.push({ method, status: response.status });
    return response;
  };
  const transport = new StreamableHTTPClientTransport(url, {
    fetch: instrumentedFetch,
    ...(token
      ? { requestInit: { headers: { authorization: `Bearer ${token}` } } }
      : {})
  });
  return {
    client: new Client({ name: "textureflow-http-test", version: "1.0.0" }),
    requests,
    transport
  };
}

describe("Streamable HTTP bridge", () => {
  it("uses the documented listener defaults", () => {
    expect(readHttpListenConfig({})).toEqual({
      host: "127.0.0.1",
      port: 8787
    });
    expect(readHttpListenConfig({
      TEXTUREFLOW_HTTP_HOST: "0.0.0.0",
      TEXTUREFLOW_HTTP_PORT: "9000"
    })).toEqual({
      host: "0.0.0.0",
      port: 9000
    });
    expect(() => readHttpListenConfig({ TEXTUREFLOW_HTTP_PORT: "0" })).toThrow(
      "TEXTUREFLOW_HTTP_PORT"
    );
  });

  it("serves health and restricts the MCP endpoint to POST, GET, and DELETE", async () => {
    const running = await startFixtureBridge();

    const health = await fetch(new URL("/healthz", running.url));
    expect(health.status).toBe(200);
    expect(await health.json()).toEqual({ status: "ok", activeSessions: 0 });

    const unsupported = await fetch(running.url, { method: "PUT" });
    expect(unsupported.status).toBe(405);
    expect(unsupported.headers.get("allow")).toBe("GET, POST, DELETE");

    const missingSession = await fetch(running.url, {
      method: "GET",
      headers: { accept: "text/event-stream" }
    });
    expect(missingSession.status).toBe(400);

    const missingRoute = await fetch(new URL("/missing", running.url));
    expect(missingRoute.status).toBe(404);
  });

  it("negotiates a stateful session and supports POST, GET, and DELETE without sensitive logs", async () => {
    const logger = new CapturingLogger();
    const token = "http-test-token-that-must-not-be-logged";
    const replyText = "Private reply text that must never reach logs.";
    const running = await startFixtureBridge({ logger });
    const { client, requests, transport } = createClient(running.url, token);

    await client.connect(transport as Transport);
    try {
      expect(transport.sessionId).toBeTruthy();
      expect(running.bridge.activeSessionCount).toBe(1);

      const attention = await client.callTool({
        name: "texture_list_attention",
        arguments: { session_id: "http-voice-session", limit: 1 }
      });
      expect(attention.isError).not.toBe(true);
      const eventBody = (
        attention.structuredContent as {
          data: { events: Array<{ body: string }> };
        }
      ).data.events[0]?.body;
      expect(eventBody).toBeTruthy();

      const prepared = await client.callTool({
        name: "texture_prepare_reply",
        arguments: {
          session_id: "http-voice-session",
          event_id: "that one",
          message: replyText
        }
      });
      expect(prepared.structuredContent).toMatchObject({
        ok: true,
        requiresConfirmation: true,
        textureCue: "CONFIRMATION_REQUIRED"
      });

      const confirmed = await client.callTool({
        name: "texture_confirm_action",
        arguments: { session_id: "http-voice-session" }
      });
      expect(confirmed.structuredContent).toMatchObject({
        ok: true,
        requiresConfirmation: false,
        textureCue: "ACTION_DISPATCHED",
        data: { receipt: { status: "DISPATCHED" } }
      });

      const sessionId = transport.sessionId;
      if (!sessionId) {
        throw new Error("Expected the SDK client to retain the MCP session ID.");
      }
      await expect.poll(
        () => requests.some((request) => request.method === "GET" && request.status === 200)
      ).toBe(true);
      expect(requests.some((request) => request.method === "POST")).toBe(true);

      const logged = JSON.stringify(logger.entries);
      expect(logged).not.toContain(token);
      expect(logged).not.toContain(replyText);
      expect(logged).not.toContain(eventBody);
      expect(logged).not.toContain(sessionId);

      await transport.terminateSession();
      expect(running.bridge.activeSessionCount).toBe(0);
      expect(requests).toContainEqual({ method: "DELETE", status: 200 });
    } finally {
      await client.close();
    }
  });

  it("creates an isolated MCP server and service for each HTTP session", async () => {
    let serviceCount = 0;
    let sessionSequence = 0;
    const running = await startFixtureBridge({
      serviceFactory: () => {
        serviceCount += 1;
        return createFixtureService();
      },
      sessionIdGenerator: () => `http-session-${++sessionSequence}`
    });
    const first = createClient(running.url);
    const second = createClient(running.url);

    await first.client.connect(first.transport as Transport);
    await second.client.connect(second.transport as Transport);
    try {
      expect(serviceCount).toBe(2);
      expect(running.bridge.activeSessionCount).toBe(2);
      expect(first.transport.sessionId).not.toBe(second.transport.sessionId);

      await first.client.callTool({
        name: "texture_list_attention",
        arguments: { limit: 1 }
      });
      const firstReference = await first.client.callTool({
        name: "texture_read_event",
        arguments: { event_id: "that one" }
      });
      const leakedReference = await second.client.callTool({
        name: "texture_read_event",
        arguments: { event_id: "that one" }
      });

      expect(firstReference.isError).not.toBe(true);
      expect(leakedReference.isError).toBe(true);

      await first.transport.terminateSession();
      expect(running.bridge.activeSessionCount).toBe(1);
      await second.transport.terminateSession();
      expect(running.bridge.activeSessionCount).toBe(0);
    } finally {
      await first.client.close();
      await second.client.close();
    }
  });
});
