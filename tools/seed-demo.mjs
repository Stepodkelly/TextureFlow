import { randomUUID } from "node:crypto";
import { ConvexHttpClient } from "convex/browser";
import { makeFunctionReference } from "convex/server";

for (const name of [
  "CONVEX_URL",
  "TEXTUREFLOW_OWNER_ID",
  "TEXTUREFLOW_BRIDGE_TOKEN",
  "TEXTUREFLOW_DEVICE_TOKEN",
]) {
  if (!process.env[name]) {
    throw new Error(`${name} is required.`);
  }
}

const ownerId = process.env.TEXTUREFLOW_OWNER_ID;
const bridgeActor = {
  ownerId,
  role: "BRIDGE",
  token: process.env.TEXTUREFLOW_BRIDGE_TOKEN,
};
const deviceActor = {
  ownerId,
  role: "DEVICE",
  deviceId: "android_demo",
  token: process.env.TEXTUREFLOW_DEVICE_TOKEN,
};
const client = new ConvexHttpClient(process.env.CONVEX_URL);
const mutation = (name, args) =>
  client.mutation(makeFunctionReference(name), args);

await mutation("devices:register", {
  actor: deviceActor,
  deviceId: "android_demo",
  displayName: "TextureFlow Rehearsal Phone",
  platform: "SIMULATOR",
  status: "REHEARSAL",
  appVersion: "textureflow-demo/1",
});
await mutation("devices:heartbeat", {
  actor: deviceActor,
  appVersion: "textureflow-demo/1",
  deviceTimestamp: new Date().toISOString(),
  traceId: `trace_seed_${randomUUID()}`,
});
const seeded = await mutation("demoFixtures:seed", { actor: bridgeActor });
for (const fixtureId of ["sam_downstairs", "maya_nine"]) {
  await mutation("demoFixtures:inject", {
    actor: bridgeActor,
    fixtureId,
    targetDeviceId: "android_demo",
    traceId: `trace_fixture_${randomUUID()}`,
  });
}

console.log(`Seeded ${seeded.total} TextureFlow rehearsal fixtures for ${ownerId}.`);
