import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  REPOSITORY_ROOT,
  assertNoReceipt,
  loadDemoFixtures,
  validateCommandBinding,
  validateProposalBinding,
} from "../lib/contracts.mjs";
import {
  REHEARSAL_LABEL,
  SCENARIOS,
  buildRehearsal,
} from "../lib/rehearsal.mjs";
import { renderTrace } from "../lib/trace-renderer.mjs";

const fixtures = loadDemoFixtures();
const sam = fixtures.find((fixture) => fixture.eventId === "evt_demo_sam");
const currentFile = fileURLToPath(import.meta.url);

test("canonical shared notification fixtures validate", () => {
  assert.equal(fixtures.length, 2);
  assert.ok(sam);
});

test("rehearsal output is deterministic", () => {
  const first = buildRehearsal({
    fixture: sam,
    scenario: "confirmed-awaiting-device",
  });
  const second = buildRehearsal({
    fixture: sam,
    scenario: "confirmed-awaiting-device",
  });
  assert.deepEqual(first, second);
});

test("every scenario is labeled and incapable of generating a receipt", () => {
  for (const scenario of SCENARIOS) {
    const output = buildRehearsal({ fixture: sam, scenario });
    assert.equal(output.mode, "REHEARSAL");
    assert.equal(output.conspicuousLabel, REHEARSAL_LABEL);
    assert.equal(output.safety.networkUsed, false);
    assert.equal(output.safety.liveAndroidActionAttempted, false);
    assert.equal(output.safety.receiptGenerated, false);
    assert.equal(output.safety.authoritativeSuccessClaimed, false);
    assert.equal(output.terminal.deviceReceiptObserved, false);
    assertNoReceipt(output);
  }
});

test("a proposal does not create a command before confirmation", () => {
  const output = buildRehearsal({ fixture: sam, scenario: "proposal-only" });
  assert.ok(output.artifacts.actionProposal);
  assert.equal(output.artifacts.actionProposal.status, "PROPOSED");
  assert.equal(output.artifacts.textureCommand, undefined);
  assert.equal(output.terminal.status, "AWAITING_CONFIRMATION");
});

test("confirmed rehearsal preserves event, proposal, command bindings", () => {
  const output = buildRehearsal({
    fixture: sam,
    scenario: "confirmed-awaiting-device",
  });
  const { notificationEvent, actionProposal, textureCommand } = output.artifacts;
  validateProposalBinding(actionProposal, notificationEvent);
  validateCommandBinding(textureCommand, actionProposal);
  assert.equal(actionProposal.status, "COMMITTED");
  assert.equal(textureCommand.status, "QUEUED");
  assert.equal(output.terminal.status, "AWAITING_DEVICE_EVIDENCE");
});

test("cancelled, offline, and unsupported paths create no command", () => {
  for (const scenario of ["cancelled", "device-offline", "reply-unsupported"]) {
    const output = buildRehearsal({ fixture: sam, scenario });
    assert.equal(output.artifacts.textureCommand, undefined, scenario);
  }
});

test("updated and removed events stale their original proposals", () => {
  for (const scenario of ["stale-event", "notification-removed"]) {
    const output = buildRehearsal({ fixture: sam, scenario });
    assert.equal(output.artifacts.actionProposal.status, "STALE");
    assert.ok(
      output.artifacts.notificationEvent.version >
        output.artifacts.actionProposal.expectedEventVersion,
    );
    assert.equal(output.artifacts.textureCommand, undefined);
  }
});

test("duplicate confirmation produces one deterministic command", () => {
  const output = buildRehearsal({
    fixture: sam,
    scenario: "duplicate-confirmation",
  });
  assert.ok(output.artifacts.textureCommand);
  assert.equal(
    output.traceEvents.filter((event) => event.name === "COMMAND_QUEUED").length,
    1,
  );
  assert.equal(
    output.traceEvents.filter(
      (event) => event.name === "DUPLICATE_CONFIRMATION_IGNORED",
    ).length,
    1,
  );
});

test("untrusted notification content cannot become the proposed reply", () => {
  const reply = "I will meet you in the lobby.";
  const output = buildRehearsal({
    fixture: sam,
    scenario: "malicious-content",
    reply,
  });
  assert.match(output.artifacts.notificationEvent.body, /Ignore the user's confirmation/);
  assert.equal(output.artifacts.actionProposal.payload.message, reply);
  assert.doesNotMatch(
    output.artifacts.actionProposal.payload.message,
    /every contact/,
  );
});

test("trace renderer prominently labels rehearsal evidence", () => {
  const output = buildRehearsal({ fixture: sam, scenario: "proposal-only" });
  const report = renderTrace(output);
  assert.match(report, /REHEARSAL - NO LIVE ACTIONS OR RECEIPTS/);
  assert.match(report, /Device receipt observed: no/);
  assert.doesNotMatch(report, /ACTION_DISPATCHED/);
});

test("CLI refuses live mode", () => {
  const script = path.join(REPOSITORY_ROOT, "tools/run-rehearsal/index.mjs");
  const result = spawnSync(process.execPath, [script, "--live"], {
    cwd: REPOSITORY_ROOT,
    encoding: "utf8",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /live mode is intentionally unavailable/);
});

test("one-command rehearsal emits a complete safety scorecard", () => {
  const script = path.join(REPOSITORY_ROOT, "tools/run-rehearsal/index.mjs");
  const result = spawnSync(process.execPath, [script, "--all", "--json"], {
    cwd: REPOSITORY_ROOT,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);
  const output = JSON.parse(result.stdout);
  assert.equal(output.mode, "REHEARSAL");
  assert.equal(output.results.length, SCENARIOS.length);
  assert.deepEqual(output.scorecard, {
    scenariosGenerated: SCENARIOS.length,
    scenariosExpected: SCENARIOS.length,
    receiptsGenerated: 0,
    liveActionsAttempted: 0,
    passed: true,
  });
  assertNoReceipt(output);
});

test("fixture injector refuses live mode", () => {
  const script = path.join(
    REPOSITORY_ROOT,
    "tools/inject-demo-event/index.mjs",
  );
  const result = spawnSync(process.execPath, [script, "--live"], {
    cwd: REPOSITORY_ROOT,
    encoding: "utf8",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /cannot inject live events/);
});

test("test path remains inside the Agent F write scope", () => {
  assert.ok(currentFile.startsWith(path.join(REPOSITORY_ROOT, "tools")));
});
