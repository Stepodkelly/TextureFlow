import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import type { ToolEnvelope } from "./contracts.js";
import type { BridgeService } from "./service.js";
import {
  ListAttentionInputSchema,
  MessagesFromInputSchema,
  PersonContextInputSchema,
  PrepareDismissInputSchema,
  PrepareReplyInputSchema,
  PrepareSnoozeInputSchema,
  ProposalActionInputSchema,
  ReadEventInputSchema,
  ReviseReplyInputSchema,
  StatusInputSchema
} from "./tools/schemas.js";

export const TOOL_NAMES = [
  "texture_status",
  "texture_list_attention",
  "texture_read_event",
  "texture_messages_from",
  "texture_person_context",
  "texture_prepare_reply",
  "texture_revise_reply",
  "texture_prepare_dismiss",
  "texture_prepare_snooze",
  "texture_confirm_action",
  "texture_cancel_action"
] as const;

function toolResult(envelope: ToolEnvelope<unknown>): CallToolResult {
  return {
    content: [{ type: "text", text: envelope.spokenSummary }],
    structuredContent: envelope as unknown as Record<string, unknown>,
    isError: !envelope.ok
  };
}

export function createMcpServer(service: BridgeService): McpServer {
  const server = new McpServer({
    name: "textureflow-voiceos-bridge",
    version: "0.1.0"
  });

  server.registerTool(
    "texture_status",
    {
      description: "Report TextureFlow mode, bridge health, Android availability, and active attention count.",
      inputSchema: StatusInputSchema.shape
    },
    async (input) => toolResult(await service.status(input))
  );

  server.registerTool(
    "texture_list_attention",
    {
      description: "List the most important active Android attention items in speech-friendly order.",
      inputSchema: ListAttentionInputSchema.shape
    },
    async (input) => toolResult(await service.listAttention(input))
  );

  server.registerTool(
    "texture_read_event",
    {
      description: "Read one active attention event by event ID or a recent reference such as 'that one'.",
      inputSchema: ReadEventInputSchema.shape
    },
    async (input) => toolResult(await service.readEvent(input))
  );

  server.registerTool(
    "texture_messages_from",
    {
      description: "Read active messages from one named person across supported applications.",
      inputSchema: MessagesFromInputSchema.shape
    },
    async (input) => toolResult(await service.messagesFrom(input))
  );

  server.registerTool(
    "texture_person_context",
    {
      description: "Return bounded recent context and open requests for one person.",
      inputSchema: PersonContextInputSchema.shape
    },
    async (input) => toolResult(await service.personContext(input))
  );

  server.registerTool(
    "texture_prepare_reply",
    {
      description: "Prepare and preview a reply. This does not send it; explicit confirmation is always required.",
      inputSchema: PrepareReplyInputSchema.shape
    },
    async (input) => toolResult(await service.prepareReply(input))
  );

  server.registerTool(
    "texture_revise_reply",
    {
      description: "Revise an active reply proposal and return a new exact preview requiring confirmation.",
      inputSchema: ReviseReplyInputSchema.shape
    },
    async (input) => toolResult(await service.reviseReply(input))
  );

  server.registerTool(
    "texture_prepare_dismiss",
    {
      description: "Prepare dismissal of one notification. This does not dismiss it until confirmed.",
      inputSchema: PrepareDismissInputSchema.shape
    },
    async (input) => toolResult(await service.prepareDismiss(input))
  );

  server.registerTool(
    "texture_prepare_snooze",
    {
      description: "Prepare snoozing one notification for a bounded number of minutes. Confirmation is required.",
      inputSchema: PrepareSnoozeInputSchema.shape
    },
    async (input) => toolResult(await service.prepareSnooze(input))
  );

  server.registerTool(
    "texture_confirm_action",
    {
      description: "Confirm exactly one active proposal, queue its command, and report only status proven by an Android receipt.",
      inputSchema: ProposalActionInputSchema.shape
    },
    async (input) => toolResult(await service.confirmAction(input))
  );

  server.registerTool(
    "texture_cancel_action",
    {
      description: "Cancel one active proposal without executing it.",
      inputSchema: ProposalActionInputSchema.shape
    },
    async (input) => toolResult(await service.cancelAction(input))
  );

  return server;
}
