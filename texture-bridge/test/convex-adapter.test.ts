import { describe, expect, it } from "vitest";
import {
  ConvexAdapter,
  type ConvexCaller
} from "../src/adapters/convex.js";
import { FixtureAdapter } from "../src/adapters/fixture.js";
import { context, fixedClock } from "./helpers.js";

class FakeCaller implements ConvexCaller {
  readonly calls: Array<{ kind: "query" | "mutation"; name: string; args: Record<string, unknown> }> = [];

  constructor(
    private readonly responses: Record<string, unknown>
  ) {}

  async query(name: string, args: Record<string, unknown>): Promise<unknown> {
    this.calls.push({ kind: "query", name, args });
    return this.responses[name];
  }

  async mutation(name: string, args: Record<string, unknown>): Promise<unknown> {
    this.calls.push({ kind: "mutation", name, args });
    return this.responses[name];
  }
}

describe("ConvexAdapter", () => {
  it("maps confirmation to the atomic Core mutation and waits for a receipt", async () => {
    const fixture = new FixtureAdapter({ now: fixedClock });
    const proposal = await fixture.prepareAction({
      ...context,
      eventId: "evt_fixture_sam_whatsapp",
      actionType: "REPLY",
      payload: { message: "On my way." }
    });
    const completed = await fixture.confirmProposal(context, proposal.proposalId);
    const { receipt, command, event } = completed;
    const coreProposal = { ...proposal, status: "COMMITTED" as const, revision: 1 };
    const caller = new FakeCaller({
      "proposals:get": coreProposal,
      "events:get": event,
      "proposals:confirm": {
        ok: true,
        proposal: coreProposal,
        command,
        receipt: null
      },
      "receipts:getByCommand": receipt
    });
    const adapter = new ConvexAdapter(caller, {
      bridgeToken: "bridge-secret",
      receiptTimeoutMs: 100,
      receiptPollMs: 1
    });

    const result = await adapter.confirmProposal(context, proposal.proposalId);

    expect(result.receipt?.status).toBe("DISPATCHED");
    expect(caller.calls.map((call) => `${call.kind}:${call.name}`)).toEqual([
      "query:proposals:get",
      "query:events:get",
      "mutation:proposals:confirm",
      "query:receipts:getByCommand"
    ]);
    expect(caller.calls[2]?.args).toMatchObject({
      actor: {
        ownerId: context.ownerId,
        role: "BRIDGE",
        token: "bridge-secret"
      },
      sessionId: context.sessionId,
      proposalId: proposal.proposalId,
      expectedRevision: 1
    });
  });

  it("rejects malformed Core output at the adapter boundary", async () => {
    const caller = new FakeCaller({ "attention:list": [{ eventId: "incomplete" }] });
    const adapter = new ConvexAdapter(caller);

    await expect(adapter.listAttention(context, 3)).rejects.toThrow();
  });
});
