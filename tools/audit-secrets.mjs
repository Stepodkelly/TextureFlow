import { execFileSync } from "node:child_process";
import { readFile, stat } from "node:fs/promises";

function parseEnv(source) {
  return source
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => {
      const separator = line.indexOf("=");
      return [line.slice(0, separator), line.slice(separator + 1)];
    });
}

const local = await readFile(new URL("../.env.local", import.meta.url), "utf8");
const convex = await readFile(new URL("../.env.convex", import.meta.url), "utf8");
const secrets = [...new Set(
  [...parseEnv(local), ...parseEnv(convex)]
    .filter(([name, value]) => /(?:KEY|TOKEN)$/u.test(name) && value.length >= 16)
    .map(([, value]) => value),
)];
const output = execFileSync(
  "git",
  ["ls-files", "--cached", "--others", "--exclude-standard", "-z"],
  { encoding: "utf8" },
);
const files = output.split("\0").filter(Boolean);
const findings = [];
for (const file of files) {
  const metadata = await stat(file);
  if (!metadata.isFile() || metadata.size > 5_000_000) continue;
  const contents = await readFile(file);
  if (secrets.some((secret) => contents.includes(Buffer.from(secret)))) findings.push(file);
}

if (findings.length > 0) {
  throw new Error(`Secret material found in unignored files: ${findings.join(", ")}`);
}
console.log(`Secret audit passed across ${files.length} unignored files.`);
