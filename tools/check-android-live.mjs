import { ConvexHttpClient } from "convex/browser";
import { makeFunctionReference } from "convex/server";

for (const name of ["CONVEX_URL", "TEXTUREFLOW_OWNER_ID", "TEXTUREFLOW_BRIDGE_TOKEN"]) {
  if (!process.env[name]) throw new Error(`${name} is required.`);
}

const client = new ConvexHttpClient(process.env.CONVEX_URL);
const actor = {
  ownerId: process.env.TEXTUREFLOW_OWNER_ID,
  role: "BRIDGE",
  token: process.env.TEXTUREFLOW_BRIDGE_TOKEN,
};
const query = (name, args) => client.query(makeFunctionReference(name), args);
const devices = await query("devices:list", { actor });
const android = devices.find(
  (device) => device.platform === "ANDROID" && device.displayName === "TextureFlow Android",
);
if (!android) throw new Error("The enrolled Android device was not registered in Convex.");
if (android.status !== "ONLINE") {
  throw new Error(`The Android device is ${android.status}, not ONLINE.`);
}
const heartbeatAgeMs = Date.now() - Date.parse(android.lastSeenAt);
if (!Number.isFinite(heartbeatAgeMs) || heartbeatAgeMs > 45_000) {
  throw new Error(`Android heartbeat is stale by ${heartbeatAgeMs}ms.`);
}
const attention = await query("attention:list", { actor, limit: 20 });

console.log(
  `Android live: ${android.deviceId}, heartbeat ${heartbeatAgeMs}ms ago, ${attention.length} active server events.`,
);
