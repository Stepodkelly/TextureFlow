# `intelligence/evals/`

Eval seam for the shared triage case set in `shared/evals/triage-cases-v2.json`.

| Type | Role |
|------|------|
| `TriageEvalTarget` | Tiny interface the JUnit runner scores. No Android imports. |

`DeterministicStubTarget` lives in tests and approximates `intelligence/src/priority.ts`
so the harness compiles before Stream A merges. After A lands, the runner should
wrap `DeterministicTriage` instead of the stub.
