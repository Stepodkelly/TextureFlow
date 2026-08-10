import { describe, expect, it } from "vitest";
import { TOOL_NAMES } from "../src/server.js";

describe("VoiceOS tool surface", () => {
  it("contains the complete bounded surface and no immediate-send tool", () => {
    expect(TOOL_NAMES).toEqual([
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
    ]);
    expect(TOOL_NAMES.some((name) => /send|execute_now/i.test(name))).toBe(false);
  });
});
