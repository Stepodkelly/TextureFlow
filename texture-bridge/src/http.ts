#!/usr/bin/env node
import { randomUUID } from "node:crypto";
import {
  createServer,
  type IncomingMessage,
  type Server as NodeHttpServer,
  type ServerResponse
} from "node:http";
import { pathToFileURL } from "node:url";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import type { Transport } from "@modelcontextprotocol/sdk/shared/transport.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { createBackend, readConfig, type BridgeConfig } from "./config.js";
import { StderrLogger, type Logger } from "./logger.js";
import { createMcpServer } from "./server.js";
import { BridgeService } from "./service.js";
import { SessionStore } from "./session.js";

const DEFAULT_HOST = "127.0.0.1";
const DEFAULT_PORT = 8787;
const DEFAULT_BODY_LIMIT_BYTES = 1_048_576;

interface HttpSession {
  closed: boolean;
  server: McpServer;
  service: BridgeService;
  sessionId?: string;
  transport: StreamableHTTPServerTransport;
}

class HttpRequestError extends Error {
  constructor(
    readonly status: number,
    readonly rpcCode: number,
    message: string
  ) {
    super(message);
    this.name = "HttpRequestError";
  }
}

export interface HttpListenConfig {
  host: string;
  port: number;
}

export interface HttpBridgeOptions {
  config?: BridgeConfig;
  keepAliveMs?: number;
  logger?: Logger;
  requestBodyLimitBytes?: number;
  serviceFactory?: () => BridgeService;
  sessionIdGenerator?: () => string;
}

export interface StartHttpBridgeOptions extends HttpBridgeOptions {
  host?: string;
  port?: number;
}

export interface RunningHttpBridge {
  bridge: TextureHttpBridge;
  close(): Promise<void>;
  host: string;
  port: number;
  server: NodeHttpServer;
  url: URL;
}

export function readHttpListenConfig(
  environment: NodeJS.ProcessEnv = process.env
): HttpListenConfig {
  const host = environment.TEXTUREFLOW_HTTP_HOST?.trim() || DEFAULT_HOST;
  const rawPort = environment.TEXTUREFLOW_HTTP_PORT;
  const port = rawPort === undefined ? DEFAULT_PORT : Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65_535) {
    throw new Error("TEXTUREFLOW_HTTP_PORT must be an integer from 1 through 65535.");
  }
  return { host, port };
}

export class TextureHttpBridge {
  private readonly allSessions = new Set<HttpSession>();
  private readonly bodyLimitBytes: number;
  private readonly keepAliveMs: number | undefined;
  private readonly logger: Logger;
  private readonly serviceFactory: () => BridgeService;
  private readonly sessionIdGenerator: () => string;
  private readonly sessions = new Map<string, HttpSession>();

  constructor(options: HttpBridgeOptions = {}) {
    this.logger = options.logger ?? new StderrLogger();
    this.bodyLimitBytes = options.requestBodyLimitBytes ?? DEFAULT_BODY_LIMIT_BYTES;
    if (!Number.isInteger(this.bodyLimitBytes) || this.bodyLimitBytes < 1) {
      throw new Error("requestBodyLimitBytes must be a positive integer.");
    }
    this.keepAliveMs = options.keepAliveMs;
    this.sessionIdGenerator = options.sessionIdGenerator ?? randomUUID;

    if (options.serviceFactory) {
      this.serviceFactory = options.serviceFactory;
    } else {
      const config = options.config ?? readConfig();
      this.serviceFactory = () => new BridgeService({
        backend: createBackend(config),
        ownerId: config.ownerId,
        sessions: new SessionStore({ ttlMs: config.sessionTtlMs }),
        logger: this.logger
      });
    }
  }

  get activeSessionCount(): number {
    return this.sessions.size;
  }

  async handleRequest(req: IncomingMessage, res: ServerResponse): Promise<void> {
    try {
      const pathname = new URL(req.url ?? "/", "http://localhost").pathname;
      if (pathname === "/healthz") {
        this.handleHealth(req, res);
        return;
      }
      if (pathname !== "/mcp") {
        writeJson(res, 404, { error: "Not found" });
        return;
      }
      if (req.method !== "POST" && req.method !== "GET" && req.method !== "DELETE") {
        res.setHeader("allow", "GET, POST, DELETE");
        writeMcpError(res, 405, -32000, "Method not allowed");
        return;
      }
      await this.handleMcp(req, res);
    } catch (error) {
      const requestError = error instanceof HttpRequestError ? error : undefined;
      const status = requestError?.status ?? 500;
      this.logger.log(status >= 500 ? "error" : "warn", "http_request_failed", {
        method: req.method ?? "UNKNOWN",
        status
      });
      if (!res.headersSent) {
        writeMcpError(
          res,
          status,
          requestError?.rpcCode ?? -32603,
          requestError?.message ?? "Internal server error"
        );
      } else if (!res.writableEnded) {
        res.end();
      }
    }
  }

  async close(): Promise<void> {
    const entries = [...this.allSessions];
    await Promise.all(entries.map(async (entry) => {
      try {
        await entry.server.close();
      } catch {
        this.logger.log("warn", "http_session_close_failed");
      } finally {
        this.cleanupSession(entry, "http_session_closed");
      }
    }));
  }

  private handleHealth(req: IncomingMessage, res: ServerResponse): void {
    if (req.method !== "GET") {
      res.setHeader("allow", "GET");
      writeJson(res, 405, { error: "Method not allowed" });
      return;
    }
    writeJson(res, 200, {
      status: "ok",
      activeSessions: this.activeSessionCount
    });
  }

  private async handleMcp(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const sessionId = readSessionId(req);
    if (sessionId) {
      const entry = this.sessions.get(sessionId);
      if (!entry) {
        writeMcpError(res, 404, -32001, "Session not found");
        return;
      }
      const parsedBody = req.method === "POST"
        ? await readJsonBody(req, this.bodyLimitBytes)
        : undefined;
      await entry.transport.handleRequest(req, res, parsedBody);
      return;
    }

    if (req.method !== "POST") {
      writeMcpError(res, 400, -32000, "Missing MCP session ID");
      return;
    }

    const parsedBody = await readJsonBody(req, this.bodyLimitBytes);
    if (!isInitializeRequest(parsedBody)) {
      writeMcpError(res, 400, -32000, "A valid initialization request is required");
      return;
    }

    const entry = await this.createSession();
    try {
      await entry.transport.handleRequest(req, res, parsedBody);
    } finally {
      if (!entry.sessionId) {
        try {
          await entry.server.close();
        } finally {
          this.cleanupSession(entry, "http_session_rejected");
        }
      }
    }
  }

  private async createSession(): Promise<HttpSession> {
    let entry: HttpSession;
    const service = this.serviceFactory();
    const server = createMcpServer(service);
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: this.sessionIdGenerator,
      ...(this.keepAliveMs === undefined ? {} : { keepAliveMs: this.keepAliveMs }),
      onsessioninitialized: (sessionId) => {
        entry.sessionId = sessionId;
        this.sessions.set(sessionId, entry);
        this.logger.log("info", "http_session_initialized", {
          activeSessions: this.activeSessionCount
        });
      },
      onsessionclosed: () => {
        this.cleanupSession(entry, "http_session_closed");
      }
    });
    entry = {
      closed: false,
      server,
      service,
      transport
    };
    this.allSessions.add(entry);

    transport.onclose = () => {
      this.cleanupSession(entry, "http_session_closed");
    };
    transport.onerror = () => {
      this.logger.log("warn", "mcp_transport_error", {
        activeSessions: this.activeSessionCount
      });
    };
    // SDK 1.30.0's HTTP class and Transport interface differ only in how
    // exactOptionalPropertyTypes represents callback setters.
    await server.connect(transport as Transport);
    return entry;
  }

  private cleanupSession(entry: HttpSession, event: string): void {
    if (entry.closed) {
      return;
    }
    entry.closed = true;
    this.allSessions.delete(entry);
    if (entry.sessionId && this.sessions.get(entry.sessionId) === entry) {
      this.sessions.delete(entry.sessionId);
    }
    this.logger.log("info", event, {
      activeSessions: this.activeSessionCount
    });
  }
}

export async function startHttpBridge(
  options: StartHttpBridgeOptions = {}
): Promise<RunningHttpBridge> {
  const environmentAddress = readHttpListenConfig();
  const host = options.host ?? environmentAddress.host;
  const requestedPort = options.port ?? environmentAddress.port;
  if (!Number.isInteger(requestedPort) || requestedPort < 0 || requestedPort > 65_535) {
    throw new Error("port must be an integer from 0 through 65535.");
  }

  const bridge = new TextureHttpBridge(options);
  const server = createServer((req, res) => {
    void bridge.handleRequest(req, res);
  });
  await listen(server, requestedPort, host);

  const address = server.address();
  if (!address || typeof address === "string") {
    await bridge.close();
    throw new Error("HTTP bridge did not receive a TCP address.");
  }
  const port = address.port;
  const urlHost = host.includes(":") ? `[${host}]` : host;
  let closePromise: Promise<void> | undefined;

  return {
    bridge,
    host,
    port,
    server,
    url: new URL(`http://${urlHost}:${port}/mcp`),
    close: () => {
      closePromise ??= closeServer(server, bridge);
      return closePromise;
    }
  };
}

function readSessionId(req: IncomingMessage): string | undefined {
  const value = req.headers["mcp-session-id"];
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function readJsonBody(req: IncomingMessage, limitBytes: number): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    let tooLarge = false;

    req.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size > limitBytes) {
        tooLarge = true;
        chunks.length = 0;
        return;
      }
      if (!tooLarge) {
        chunks.push(chunk);
      }
    });
    req.on("end", () => {
      if (tooLarge) {
        reject(new HttpRequestError(413, -32000, "Request body is too large"));
        return;
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new HttpRequestError(400, -32700, "Invalid JSON request body"));
      }
    });
    req.on("error", () => {
      reject(new HttpRequestError(400, -32700, "Could not read request body"));
    });
  });
}

function writeJson(res: ServerResponse, status: number, value: unknown): void {
  res.writeHead(status, {
    "cache-control": "no-store",
    "content-type": "application/json; charset=utf-8"
  });
  res.end(JSON.stringify(value));
}

function writeMcpError(
  res: ServerResponse,
  status: number,
  code: number,
  message: string
): void {
  writeJson(res, status, {
    jsonrpc: "2.0",
    error: { code, message },
    id: null
  });
}

function listen(server: NodeHttpServer, port: number, host: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const onError = (error: Error) => {
      server.off("listening", onListening);
      reject(error);
    };
    const onListening = () => {
      server.off("error", onError);
      resolve();
    };
    server.once("error", onError);
    server.once("listening", onListening);
    server.listen(port, host);
  });
}

async function closeServer(
  server: NodeHttpServer,
  bridge: TextureHttpBridge
): Promise<void> {
  await bridge.close();
  await new Promise<void>((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
      } else {
        resolve();
      }
    });
  });
}

async function main(): Promise<void> {
  const config = readConfig();
  const logger = new StderrLogger();
  const address = readHttpListenConfig();
  const running = await startHttpBridge({
    config,
    host: address.host,
    port: address.port,
    logger
  });
  logger.log("info", "http_bridge_started", {
    adapter: config.adapter,
    host: running.host,
    port: running.port
  });

  let shuttingDown = false;
  const shutdown = async () => {
    if (shuttingDown) {
      return;
    }
    shuttingDown = true;
    logger.log("info", "http_bridge_stopping", {
      activeSessions: running.bridge.activeSessionCount
    });
    await running.close();
  };
  process.once("SIGINT", () => void shutdown());
  process.once("SIGTERM", () => void shutdown());
}

const entryPoint = process.argv[1];
if (entryPoint && import.meta.url === pathToFileURL(entryPoint).href) {
  main().catch(() => {
    console.error(JSON.stringify({
      timestamp: new Date().toISOString(),
      level: "error",
      event: "http_bridge_start_failed"
    }));
    process.exitCode = 1;
  });
}
