# TextureFlow Convex live smoke

This bounded Node harness verifies the deployed Convex lifecycle against the
contracts in `convex/**` and `shared/contracts/**`:

1. register and heartbeat a simulator device;
2. upsert a synthetic notification and read it through attention;
3. create, revise, and confirm a reply proposal;
4. claim and start the resulting command;
5. record and query an intentionally failed receipt; and
6. mark the synthetic notification removed so it leaves the attention view.

Every response is validated at runtime. Missing fields, invalid enum values,
wrong state transitions, unexpected IDs, and malformed timestamps cause a
non-zero exit with a `ContractMismatchError`.

## Run

Run from the repository root with Node and the existing `convex` dependency:

```sh
set -a
source .env.local
set +a
node tools/live-smoke/run.mjs
```

Required environment variables:

- `CONVEX_URL`
- `TEXTUREFLOW_BRIDGE_TOKEN`
- `TEXTUREFLOW_DEVICE_TOKEN`
- `TEXTUREFLOW_USER_TOKEN`

Optional bounded settings:

- `TEXTUREFLOW_SMOKE_OWNER_ID` defaults to `textureflow-live-smoke`.
- `TEXTUREFLOW_SMOKE_DEVICE_ID` defaults to `textureflow-smoke-simulator-v1`.
- `TEXTUREFLOW_SMOKE_STEP_TIMEOUT_MS` defaults to 10 seconds and is capped at
  20 seconds.
- `TEXTUREFLOW_SMOKE_RUN_TIMEOUT_MS` defaults to 60 seconds and is capped at
  120 seconds.

The runner never logs request arguments or role tokens. Error output is also
redacted against every supplied token before it reaches stderr.

## Safety boundary

This is a backend integration check, not an Android end-to-end execution test.

- The device is always registered as a `REHEARSAL` `SIMULATOR`.
- The notification, person, conversation, reply text, and identifiers are
  synthetic fixture data.
- The script has no Android, ADB, IPC, notification-listener, or `PendingIntent`
  integration.
- It never records `DISPATCHED`. After command claim/start, it records
  `FAILED` with `POLICY_BLOCKED` and the `ACTION_FAILED` texture cue, accurately
  stating that execution stopped before an Android action.
- The active fixture event is removed in cleanup. Convex proposal, command,
  receipt, and trace records remain as intentional audit evidence.

A passing run proves the deployed backend's role authentication, function
signatures, state transitions, and receipt persistence. It does not prove that
an Android notification action can execute.
