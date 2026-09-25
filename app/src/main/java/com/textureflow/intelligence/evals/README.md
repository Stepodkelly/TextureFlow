# `intelligence/evals/`

Eval seam for the shared triage case set in `shared/evals/triage-cases-v2.json`.

| Type | Role |
|------|------|
| `TriageEvalTarget` | Tiny interface the JUnit runner scores. No Android imports. |

`DeterministicTriageTarget` wraps Stream A’s scorer. `DeterministicStubTarget`
stays in tests as a regression check against the pre-A regex copy.
