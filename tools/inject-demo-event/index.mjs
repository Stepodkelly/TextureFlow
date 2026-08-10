#!/usr/bin/env node
import { findFixture, loadDemoFixtures } from "../lib/contracts.mjs";

function optionValue(args, option, fallback) {
  const index = args.indexOf(option);
  if (index === -1) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith("--")) {
    throw new Error(`${option} requires a value`);
  }
  return args[index + 1];
}

try {
  const args = process.argv.slice(2);
  if (args.includes("--mode") || args.includes("--live")) {
    throw new Error(
      "This tool supports REHEARSAL only and cannot inject live events",
    );
  }
  const fixtures = loadDemoFixtures();
  if (args.includes("--list")) {
    for (const fixture of fixtures) {
      console.log(`${fixture.eventId}\t${fixture.sender.displayName}\t${fixture.app.label}`);
    }
    process.exit(0);
  }

  const eventId = optionValue(args, "--fixture", fixtures[0]?.eventId);
  const event = findFixture(fixtures, eventId);
  const envelope = {
    formatVersion: 1,
    mode: "REHEARSAL",
    conspicuousLabel: "REHEARSAL - SYNTHETIC NOTIFICATION EVENT",
    kind: "NOTIFICATION_EVENT_FIXTURE",
    source: "shared/fixtures/demo-events.json",
    networkUsed: false,
    liveAndroidActionAttempted: false,
    event,
  };

  console.log(JSON.stringify(envelope, null, args.includes("--compact") ? 0 : 2));
} catch (error) {
  console.error(`REHEARSAL injector failed: ${error.message}`);
  process.exitCode = 1;
}

