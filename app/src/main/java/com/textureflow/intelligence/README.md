# `intelligence/` — on-device attention engine

Java port of the TypeScript `intelligence/` helper. Wave 0 freezes the public
types. Later streams fill in triage, shield, ledger, and the engine.

| Folder | Role | Owner |
|--------|------|--------|
| `api/` | Frozen contracts: engine, assessments, drafts, job classes | Wave 0 (changes need coordinator approval) |
| `shield/` | Context shield, token budget, untrusted wrap, injection flag | Stream B |
| `policy/` | PolicyGate §6.4 rules, schema validator, proposal binding | Stream B |

`api/` must stay free of Android notification handles. No `execute` / `send`
methods. The TypeScript package remains the ranking oracle until Stream A
proves parity.
