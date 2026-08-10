import { readFile } from "node:fs/promises";

function parseEnv(source) {
  return Object.fromEntries(
    source
      .split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#"))
      .map((line) => {
        const separator = line.indexOf("=");
        if (separator < 1) {
          throw new Error("Invalid environment file entry.");
        }
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
}

const local = parseEnv(await readFile(new URL("../.env.local", import.meta.url), "utf8"));
const roleTokens = parseEnv(
  await readFile(new URL("../.env.convex", import.meta.url), "utf8"),
);
const required = [
  "TEXTUREFLOW_BRIDGE_TOKEN",
  "TEXTUREFLOW_DEVICE_TOKEN",
  "TEXTUREFLOW_USER_TOKEN",
];

if (!local.CONVEX_API_KEY || !local.CONVEX_URL) {
  throw new Error("CONVEX_API_KEY and CONVEX_URL are required in .env.local.");
}
for (const name of required) {
  if (!roleTokens[name]) {
    throw new Error(`${name} is required in .env.convex.`);
  }
}

const response = await fetch(`${local.CONVEX_URL}/api/update_environment_variables`, {
  method: "POST",
  headers: {
    Authorization: `Convex ${local.CONVEX_API_KEY}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    changes: required.map((name) => ({ name, value: roleTokens[name] })),
  }),
});

if (!response.ok) {
  const detail = (await response.text()).slice(0, 500);
  throw new Error(`Convex environment update failed (${response.status}): ${detail}`);
}

console.log(`Configured ${required.length} TextureFlow role variables on the dev deployment.`);
