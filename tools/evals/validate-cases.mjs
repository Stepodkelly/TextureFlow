#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const path = resolve(root, "shared/evals/triage-cases-v2.json");
const allowedTags = new Set(["injection", "promo", "urgent", "ambiguous", "multilingual", "emoji", "long"]);
const allowedLevels = new Set(["LOW", "NORMAL", "IMPORTANT", "URGENT"]);

const cases = JSON.parse(readFileSync(path, "utf8"));
if (!Array.isArray(cases)) {
  throw new Error("triage-cases-v2.json must be an array");
}

const ids = new Set();
const tags = {};
const problems = [];

for (const item of cases) {
  for (const field of ["id", "app", "sender", "relationship", "body", "ageMinutes", "expectedLevel", "expectedRequiresResponse", "tags"]) {
    if (!(field in item)) {
      problems.push(`${item.id ?? "?"} missing ${field}`);
    }
  }
  if (ids.has(item.id)) {
    problems.push(`duplicate id ${item.id}`);
  }
  ids.add(item.id);
  if (!allowedLevels.has(item.expectedLevel)) {
    problems.push(`${item.id} bad expectedLevel ${item.expectedLevel}`);
  }
  if (typeof item.expectedRequiresResponse !== "boolean") {
    problems.push(`${item.id} expectedRequiresResponse must be boolean`);
  }
  if (!Array.isArray(item.tags)) {
    problems.push(`${item.id} tags must be an array`);
    continue;
  }
  for (const tag of item.tags) {
    if (!allowedTags.has(tag)) {
      problems.push(`${item.id} unknown tag ${tag}`);
    }
    tags[tag] = (tags[tag] ?? 0) + 1;
  }
}

if (problems.length) {
  console.error(problems.join("\n"));
  process.exit(1);
}

console.log(`ok ${cases.length} cases`);
for (const tag of [...allowedTags].sort()) {
  console.log(`${tag}\t${tags[tag] ?? 0}`);
}
