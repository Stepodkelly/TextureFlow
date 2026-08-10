#!/usr/bin/env node
import {
  DEMO_FIXTURE_PATH,
  assertCanonicalContractVersion,
  loadDemoFixtures,
} from "../lib/contracts.mjs";

try {
  assertCanonicalContractVersion();
  const fixtures = loadDemoFixtures();
  console.log(
    `PASS contract v1: ${fixtures.length} notification fixtures validated from ${DEMO_FIXTURE_PATH}`,
  );
  for (const fixture of fixtures) {
    console.log(
      `  ${fixture.eventId}: ${fixture.sender.displayName} via ${fixture.app.label} v${fixture.version}`,
    );
  }
} catch (error) {
  console.error(`FAIL contract validation: ${error.message}`);
  process.exitCode = 1;
}

