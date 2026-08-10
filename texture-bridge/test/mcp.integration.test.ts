import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { describe, expect, it } from "vitest";
import { createMcpServer, TOOL_NAMES } from "../src/server.js";
import { createFixtureService } from "./helpers.js";

describe("MCP integration", () => {
  it("negotiates, discovers tools, and returns a structured attention envelope", async () => {
    const server = createMcpServer(createFixtureService());
    const client = new Client({ name: "textureflow-test-client", version: "1.0.0" });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();

    await server.connect(serverTransport);
    await client.connect(clientTransport);

    try {
      const listed = await client.listTools();
      expect(listed.tools.map((tool) => tool.name)).toEqual(TOOL_NAMES);

      const result = await client.callTool({
        name: "texture_list_attention",
        arguments: { session_id: "mcp-test", limit: 1 }
      });
      expect(result.isError).not.toBe(true);
      expect(result.structuredContent).toMatchObject({
        ok: true,
        requiresConfirmation: false,
        textureCue: "ATTENTION_URGENT"
      });
      expect(Array.isArray(result.content)).toBe(true);
      if (!Array.isArray(result.content)) {
        throw new Error("Expected MCP content blocks.");
      }
      expect(result.content[0]).toMatchObject({
        type: "text"
      });
    } finally {
      await client.close();
      await server.close();
    }
  });
});
