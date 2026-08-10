import { describe, expect, it } from "vitest";
import { FixtureAdapter } from "../src/adapters/fixture.js";
import type { ConfirmationResult } from "../src/types.js";
import { createFixtureService, fixedClock } from "./helpers.js";

class ReceiptlessFixtureAdapter extends FixtureAdapter {
  override async confirmProposal(
    ...args: Parameters<FixtureAdapter["confirmProposal"]>
  ): Promise<ConfirmationResult> {
    const result = await super.confirmProposal(...args);
    const { receipt: _receipt, ...withoutReceipt } = result;
    return withoutReceipt;
  }
}

describe("BridgeService", () => {
  it("supports a speech-friendly referenced reply flow", async () => {
    const service = createFixtureService();
    const attention = await service.listAttention({
      session_id: "voice-session-a",
      limit: 2
    });
    expect(attention.ok).toBe(true);
    expect(attention.spokenSummary).toContain("Sam");

    const prepared = await service.prepareReply({
      session_id: "voice-session-a",
      event_id: "that one",
      message: "I'm coming down now."
    });
    expect(prepared.requiresConfirmation).toBe(true);
    expect(prepared.proposalId).toMatch(/^prop_fixture_/);
    expect(prepared.spokenSummary).toContain("Reply to Sam on WhatsApp");

    const revised = await service.reviseReply({
      session_id: "voice-session-a",
      message: "Meet me in the lobby."
    });
    expect(revised.requiresConfirmation).toBe(true);
    expect(revised.spokenSummary).toContain("Meet me in the lobby");

    const confirmed = await service.confirmAction({
      session_id: "voice-session-a"
    });
    expect(confirmed.ok).toBe(true);
    expect(confirmed.requiresConfirmation).toBe(false);
    expect(confirmed.textureCue).toBe("ACTION_DISPATCHED");
    expect(confirmed.spokenSummary).toBe(
      "The reply to Sam was dispatched through WhatsApp."
    );
  });

  it("does not claim dispatch without a device receipt", async () => {
    const service = createFixtureService(
      new ReceiptlessFixtureAdapter({ now: fixedClock })
    );
    await service.listAttention({ session_id: "receipt-test", limit: 1 });
    await service.prepareReply({
      session_id: "receipt-test",
      event_id: "that one",
      message: "On my way."
    });
    const confirmed = await service.confirmAction({ session_id: "receipt-test" });

    expect(confirmed.ok).toBe(true);
    expect(confirmed.textureCue).toBe("EXECUTION_STARTED");
    expect(confirmed.spokenSummary).toContain("waiting for the phone's execution receipt");
    expect(confirmed.spokenSummary).not.toMatch(/was dispatched|was sent/i);
  });

  it("cancels an active proposal without executing it", async () => {
    const service = createFixtureService();
    await service.listAttention({ session_id: "cancel-test", limit: 1 });
    await service.prepareDismiss({
      session_id: "cancel-test",
      event_id: "that one"
    });
    const cancelled = await service.cancelAction({ session_id: "cancel-test" });
    const confirmAfterCancel = await service.confirmAction({ session_id: "cancel-test" });

    expect(cancelled.ok).toBe(true);
    expect(cancelled.textureCue).toBe("CANCELLED");
    expect(cancelled.spokenSummary).toContain("Nothing was executed");
    expect(confirmAfterCancel.ok).toBe(false);
    expect(confirmAfterCancel.error?.code).toBe("PROPOSAL_NOT_FOUND");
  });

  it("validates all inputs and returns a safe tool envelope", async () => {
    const service = createFixtureService();
    const result = await service.prepareSnooze({
      event_id: "evt_fixture_sam_whatsapp",
      minutes: 0,
      unexpected: true
    });

    expect(result.ok).toBe(false);
    expect(result.error?.code).toBe("INVALID_INPUT");
    expect(result.requiresConfirmation).toBe(false);
  });
});
