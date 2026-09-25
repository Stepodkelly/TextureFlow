# Eval tools (`tools/evals/`)

Stream D owns this folder except `export-golden.mjs` (Stream A).

| Script | What it does |
|--------|----------------|
| `validate-cases.mjs` | Checks `shared/evals/triage-cases-v2.json` schema, unique ids, and tag counts |
| `write-triage-cases.mjs` | Regenerates the v2 JSON from the checked-in case list |
| `summarize-report.mjs` | Prints `app/build/reports/evals/triage.json` after the JUnit runner |

The Android runner is `TriageEvalRunnerTest`. It loads the v2 cases, scores a
`TriageEvalTarget`, and writes:

- `app/build/reports/evals/triage.json`
- `app/build/reports/evals/triage.md`

Injection pass rate **100%** is a hard JUnit failure. Other metrics are reported
only until a post-A baseline is agreed.
