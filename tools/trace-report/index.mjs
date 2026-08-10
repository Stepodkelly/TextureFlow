#!/usr/bin/env node
import fs from "node:fs";
import { renderTrace } from "../lib/trace-renderer.mjs";

function optionValue(args, option) {
  const index = args.indexOf(option);
  if (index === -1) return undefined;
  if (!args[index + 1] || args[index + 1].startsWith("--")) {
    throw new Error(`${option} requires a value`);
  }
  return args[index + 1];
}

async function readStdin() {
  let content = "";
  for await (const chunk of process.stdin) content += chunk;
  return content;
}

try {
  const args = process.argv.slice(2);
  const inputPath = optionValue(args, "--input");
  let source;
  if (inputPath) {
    source = fs.readFileSync(inputPath, "utf8");
  } else if (!process.stdin.isTTY) {
    source = await readStdin();
  } else {
    throw new Error(
      "Provide --input <rehearsal.json> or pipe JSON from run-rehearsal --json",
    );
  }
  console.log(renderTrace(JSON.parse(source)));
} catch (error) {
  console.error(`Trace report failed: ${error.message}`);
  process.exitCode = 1;
}

