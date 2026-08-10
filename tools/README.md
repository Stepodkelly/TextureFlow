# TextureFlow QA tools

These dependency-free Node tools operate in `REHEARSAL` mode only. They read
canonical fixtures from `shared/fixtures/demo-events.json`, validate contract
version 1, and never access credentials, call the network, execute an Android
action, or create an `ActionReceipt`.

```bash
node tools/validate-contracts/index.mjs
node tools/inject-demo-event/index.mjs --list
node tools/inject-demo-event/index.mjs --fixture evt_demo_sam
node tools/run-rehearsal/index.mjs --list
node tools/run-rehearsal/index.mjs --scenario proposal-only
node tools/run-rehearsal/index.mjs --scenario confirmed-awaiting-device
node tools/run-rehearsal/index.mjs --all
node tools/run-rehearsal/index.mjs --scenario stale-event --json \
  | node tools/trace-report/index.mjs
node --test tools/test/*.test.mjs
```

`inject-demo-event` prints a labeled envelope to stdout. An integration-owned
adapter may deliberately unwrap its `event` field and pass it to a local or
cloud fixture mutation. The injector itself has no endpoint or secret support,
which prevents an offline rehearsal command from being mistaken for live
ingestion.

The confirmed rehearsal queues a contract-valid `TextureCommand` but stops at
`AWAITING_DEVICE_EVIDENCE`. Only the authenticated Android execution path may
produce a receipt or claim `DISPATCHED`.

