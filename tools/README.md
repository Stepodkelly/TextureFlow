# Tools (`tools/`)

Scripts for **checking**, **demoing**, and **debugging**.

You usually run these from the repo root with Node.

## Common scripts

| Script | What it does |
|--------|----------------|
| `live-smoke/run.mjs` | Safe end-to-end smoke (synthetic; blocks real dispatch) |
| `verify-bridge-live.mjs` | Checks the voice bridge against Convex |
| `seed-demo.mjs` | Seeds demo data |
| `audit-secrets.mjs` | Looks for accidental secrets |
| `validate-contracts/` | Contract checks |
| `run-rehearsal/` | Rehearsal / demo flow helpers |

See also root `package.json` scripts (`npm run check`, etc.).

## Beginner tip

Prefer `live-smoke` before any real-device demo. It is designed to stop before
dangerous execution.
