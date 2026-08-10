#!/usr/bin/env node
import { findFixture, loadDemoFixtures } from "../lib/contracts.mjs";
import {
  REHEARSAL_LABEL,
  SCENARIOS,
  buildRehearsal,
} from "../lib/rehearsal.mjs";
import { renderTrace } from "../lib/trace-renderer.mjs";

function optionValue(args, option, fallback) {
  const index = args.indexOf(option);
  if (index === -1) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith("--")) {
    throw new Error(`${option} requires a value`);
  }
  return args[index + 1];
}

function printResult(result, json) {
  if (json) {
    console.log(JSON.stringify(result, null, 2));
    return;
  }
  console.log(renderTrace(result));
  console.log("\nArtifacts:");
  console.log(`  event:    ${result.artifacts.notificationEvent.eventId}`);
  console.log(
    `  proposal: ${result.artifacts.actionProposal?.proposalId ?? "none"}`,
  );
  console.log(
    `  command:  ${result.artifacts.textureCommand?.commandId ?? "none"}`,
  );
  console.log("  receipt:  none (Android-only authority)");
}

try {
  const args = process.argv.slice(2);
  if (args.includes("--mode") || args.includes("--live")) {
    throw new Error(`${REHEARSAL_LABEL}; live mode is intentionally unavailable`);
  }
  if (args.includes("--list")) {
    console.log(SCENARIOS.join("\n"));
    process.exit(0);
  }

  const fixtures = loadDemoFixtures();
  const eventId = optionValue(args, "--fixture", fixtures[0]?.eventId);
  const fixture = findFixture(fixtures, eventId);
  const reply = optionValue(args, "--message", undefined);
  const json = args.includes("--json");

  if (args.includes("--all")) {
    const results = SCENARIOS.map((scenario) =>
      buildRehearsal({ fixture, scenario, ...(reply ? { reply } : {}) }),
    );
    const scorecard = {
      scenariosGenerated: results.length,
      scenariosExpected: SCENARIOS.length,
      receiptsGenerated: 0,
      liveActionsAttempted: 0,
      passed: results.length === SCENARIOS.length,
    };
    if (json) {
      console.log(
        JSON.stringify(
          {
            mode: "REHEARSAL",
            conspicuousLabel: REHEARSAL_LABEL,
            results,
            scorecard,
          },
          null,
          2,
        ),
      );
    } else {
      for (const result of results) {
        printResult(result, false);
        console.log("\n");
      }
      console.log("REHEARSAL SCORECARD");
      console.log(
        `  scenarios:    ${scorecard.scenariosGenerated}/${scorecard.scenariosExpected}`,
      );
      console.log(`  receipts:     ${scorecard.receiptsGenerated}`);
      console.log(`  live actions: ${scorecard.liveActionsAttempted}`);
      console.log(`  result:       ${scorecard.passed ? "PASS" : "FAIL"}`);
    }
  } else {
    const scenario = optionValue(args, "--scenario", "proposal-only");
    const result = buildRehearsal({ fixture, scenario, ...(reply ? { reply } : {}) });
    printResult(result, json);
  }
} catch (error) {
  console.error(`REHEARSAL failed: ${error.message}`);
  process.exitCode = 1;
}
