# TextureFlow Core

This directory contains the Convex coordination plane for TextureFlow. Android
remains authoritative for live notification state and action execution. Core
stores synchronized events, person context, proposals, confirmation grants,
commands, authenticated device receipts, and redacted distributed traces.

## Setup

The integration lead owns root configuration. The gitignored root environment
file uses these existing names:

- `CONVEX_API_KEY`
- `CONVEX_URL`
- `CONVEX_DEPLOYMENT`

Never place credential values in this directory or commit them. Once the root
JavaScript package includes the current `convex` dependency, run from the
workspace root:

```bash
npx convex dev
```

This generates `convex/_generated` and pushes the schema to the configured
development deployment. Deploy the frozen hackathon backend with:

```bash
npx convex deploy
```

The preferred authentication path is OIDC. TextureFlow recognizes these custom
claims in addition to the standard token identifier:

- `textureflow_owner_id`
- `textureflow_role`: `USER`, `BRIDGE`, or `DEVICE`
- `textureflow_device_id` for device identities

For a short-lived synthetic hackathon account only, the Convex deployment may
define `TEXTUREFLOW_USER_TOKEN`, `TEXTUREFLOW_BRIDGE_TOKEN`, and
`TEXTUREFLOW_DEVICE_TOKEN`. Set these in the Convex deployment environment, not
in source. The static-token path is explicitly a demo fallback and should be
removed before real-user testing.

## Function surface

| Module | Public functions |
| --- | --- |
| `devices` | `register`, `heartbeat`, `setOffline`, `list` |
| `events` | `upsert`, `markRemoved`, `get` |
| `attention` | `list`, `weave` |
| `people` | `upsert`, `addIdentity`, `context` |
| `proposals` | `create`, `revise`, `cancel`, `confirm`, `get` |
| `commands` | `forDevice`, `claim`, `startExecution`, `get` |
| `receipts` | `complete`, `getByCommand` |
| `traces` | `append`, `list`, `recent` |
| `demoFixtures` | `seed`, `list`, `inject` |

Every call carries an `actor` envelope. OIDC claims are checked first. Demo
tokens are accepted only when no authenticated identity is present and the
corresponding deployment variable is configured.

## Safety invariants

- Event versions are monotonic. An exact retry is idempotent; conflicting data
  at the same version is rejected.
- Every higher event version and removal stales pending proposals and commands.
- A proposal is bound to an event version, device, session, exact payload
  fingerprint, and short expiry.
- `proposals:confirm` inserts the confirmation grant and queued command in one
  Convex mutation. No immediate-execution function exists.
- Command IDs and idempotency keys are deterministic per proposal revision.
- A command claim requires a client-generated token persisted by Android.
  Repeating the same claim is safe; a different token cannot take it over.
- `receipts:complete` accepts only the registered target device, matching claim,
  command, owner, and trace. One command can have only one terminal receipt.
- Rehearsal events are labeled and cannot create receipts or execution-proof
  trace spans.
- Trace attributes reject message-like fields and are limited to redacted
  operational metadata.

`traces:list` applies the receipt proof rule when projecting evidence. A live
span is `DEVICE_EVIDENCE` only if its command has one matching device receipt,
the target device and trace match, the command was claimed before expiry, and
the terminal command status equals the receipt status. Trace text alone never
proves execution.

## Validation

The deterministic test imports the same pure state helpers used by Convex and
checks the shared fixtures, enum values, event version rules, proposal policy,
trace shape, and receipt evidence predicate:

```bash
npm run check:contracts
```

The runner relaunches itself with Node's type-stripping flag so it can import
the production TypeScript policy helpers without requiring another test runtime.

After `convex/_generated` exists, also run the root TypeScript/Convex codegen
check selected by the integration lead before deployment.

## Integration order

1. Register Android with a device-specific OIDC subject or the temporary device
   token, then send heartbeats at an interval below 45 seconds.
2. Upload notification snapshots through `events:upsert`; use increasing
   versions and `events:markRemoved` for disappearance.
3. Bridge prepares with `proposals:create` and speaks `spokenPreview` exactly.
4. Bridge confirms with the same session and revision. Core atomically returns
   the queued command.
5. Android subscribes to `commands:forDevice`, persists a random claim token,
   calls `claim`, revalidates locally, then calls `startExecution`.
6. Android submits its terminal evidence through `receipts:complete`. Bridge
   waits on `receipts:getByCommand` and reports only the recorded status.

The Android outbox should retry the same event version and receipt identity;
Core treats both paths idempotently without relaxing conflict detection.
