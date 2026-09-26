# `intelligence/jobs/` — P6 job classes (Stream G)

Local-only registry. Depth stays off (`allowDepth=false`).

| Id | Output schema |
|----|----------------|
| `triage` | `triage` |
| `summary` | `summarizer` |
| `draft` | `drafter` |

`JobClassRegistry.require(id)` throws on unknown ids. The engine uses this
before running a tick, summary, or draft.
