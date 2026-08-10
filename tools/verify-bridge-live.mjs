import {
  ConvexAdapter,
  HttpConvexCaller,
} from "../texture-bridge/dist/adapters/convex.js";

for (const name of ["CONVEX_URL", "TEXTUREFLOW_OWNER_ID", "TEXTUREFLOW_BRIDGE_TOKEN"]) {
  if (!process.env[name]) {
    throw new Error(`${name} is required.`);
  }
}

const backend = new ConvexAdapter(new HttpConvexCaller(process.env.CONVEX_URL), {
  bridgeToken: process.env.TEXTUREFLOW_BRIDGE_TOKEN,
  receiptTimeoutMs: 500,
  receiptPollMs: 50,
});
const context = {
  ownerId: process.env.TEXTUREFLOW_OWNER_ID,
  sessionId: `bridge_verify_${Date.now()}`,
  traceId: `trace_bridge_verify_${Date.now()}`,
};

const status = await backend.getStatus(context);
const events = await backend.listAttention(context, 5);
if (events.length < 2) {
  throw new Error(`Expected at least two seeded events, received ${events.length}.`);
}
const sam = events.find((event) => event.sender.displayName === "Sam");
if (!sam) {
  throw new Error("The Sam rehearsal fixture was not returned by the live bridge adapter.");
}
const person = await backend.personContext(context, "Sam");
const proposal = await backend.prepareAction({
  ...context,
  eventId: sam.eventId,
  actionType: "REPLY",
  payload: { message: "I am coming down now." },
});
const cancelled = await backend.cancelProposal(context, proposal.proposalId);
if (cancelled.status !== "CANCELLED") {
  throw new Error(`Expected a cancelled proposal, received ${cancelled.status}.`);
}

console.log(
  `Live bridge verified: ${status.device.label}, ${events.length} attention items, ${person.displayName} context, proposal cancellation.`,
);
