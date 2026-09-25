#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const reportPath = resolve(root, "app/build/reports/evals/triage.json");
const report = JSON.parse(readFileSync(reportPath, "utf8"));
const metrics = report.metrics ?? {};
console.log(`target\t${report.target}`);
console.log(`cases\t${report.counts?.cases}`);
for (const [key, value] of Object.entries(metrics)) {
  console.log(`${key}\t${value}`);
}
if (report.tagCounts) {
  console.log("tags");
  for (const [tag, count] of Object.entries(report.tagCounts)) {
    console.log(`  ${tag}\t${count}`);
  }
}
