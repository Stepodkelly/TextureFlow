import { spawnSync } from "node:child_process";

const token = process.env.TEXTUREFLOW_DEVICE_TOKEN;
if (!token || !/^[a-f0-9]{64}$/u.test(token)) {
  throw new Error("A 64-character TEXTUREFLOW_DEVICE_TOKEN is required.");
}

const adb = `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`;
function run(args) {
  const result = spawnSync(adb, args, { encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error(`ADB enrollment step failed: ${result.stderr.trim()}`);
  }
}

// The setup dialog must already be visible. The token is read from the ignored
// environment file and is never printed or embedded in the APK.
run(["shell", "input", "tap", "540", "1300"]);
run(["shell", "input", "text", token]);
console.log("Entered the scoped token into TextureFlow's runtime enrollment dialog.");
