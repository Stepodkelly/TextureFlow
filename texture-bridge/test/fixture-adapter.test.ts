import { describe, expect, it } from "vitest";
import { FixtureAdapter } from "../src/adapters/fixture.js";
import { context, fixedClock } from "./helpers.js";

describe("FixtureAdapter", () => {
  it("preserves prepare -> confirm -> command -> receipt and is idempotent", async () => {
    const adapter = new FixtureAdapter({ now: fixedClock });
    const proposal = await adapter.prepareAction({
      ...context,
      eventId: "evt_fixture_sam_whatsapp",
      actionType: "REPLY",
      payload: { message: "I'm coming down." }
    });

    expect(proposal.status).toBe("PROPOSED");
    expect((await adapter.getStatus(context)).pendingProposalCount).toBe(1);

    const first = await adapter.confirmProposal(context, proposal.proposalId);
    const duplicate = await adapter.confirmProposal(context, proposal.proposalId);

    expect(first.proposal.status).toBe("COMMITTED");
    expect(first.command.status).toBe("DISPATCHED");
    expect(first.receipt?.status).toBe("DISPATCHED");
    expect(duplicate.command.commandId).toBe(first.command.commandId);
    expect(duplicate.receipt?.receiptId).toBe(first.receipt?.receiptId);
  });

  it("rejects confirmation when the Android event version changed", async () => {
    const adapter = new FixtureAdapter({ now: fixedClock });
    const proposal = await adapter.prepareAction({
      ...context,
      eventId: "evt_fixture_sam_whatsapp",
      actionType: "REPLY",
      payload: { message: "On my way." }
    });
    const event = await adapter.readEvent(context, proposal.eventId);
    adapter.replaceEventForTest({
      ...event,
      version: event.version + 1,
      status: "UPDATED",
      body: "The door opened."
    });

    await expect(adapter.confirmProposal(context, proposal.proposalId)).rejects.toMatchObject({
      code: "EVENT_CHANGED"
    });
  });

  it("cancels without creating a command", async () => {
    const adapter = new FixtureAdapter({ now: fixedClock });
    const proposal = await adapter.prepareAction({
      ...context,
      eventId: "evt_fixture_calendar",
      actionType: "DISMISS",
      payload: {}
    });
    const cancelled = await adapter.cancelProposal(context, proposal.proposalId);

    expect(cancelled.status).toBe("CANCELLED");
    await expect(adapter.confirmProposal(context, proposal.proposalId)).rejects.toMatchObject({
      code: "POLICY_BLOCKED"
    });
  });
});
