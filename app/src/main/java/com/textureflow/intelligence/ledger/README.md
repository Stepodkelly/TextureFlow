# `intelligence/ledger/` — attention metrics store

Append-only metrics for ticks, assessments, role runs, and proposals.
Tick / assessment / role rows never store message bodies. Proposal
payload text is kept only until the proposal reaches a terminal status.

| File | Role |
|------|------|
| `IntelligenceLedger.java` | Write API: ticks, assessments, role runs, proposals, capability profile, 14-day purge |
| `InMemoryLedger.java` | Full fake for other streams' JVM unit tests. No `android.*` imports. |
| `SqliteLedger.java` | Production store on `TextureFlowDatabase` v2. Android SQLite. |
| Record types | `TickRecord`, `AssessmentRecord`, `RoleRunRecord`, `ProposalRecord` |

Retention is 14 days (`IntelligenceLedger.RETENTION_MILLIS`).
`NotificationHealthJobService` calls `purgeOlderThan` on the shared
`NotificationRuntime` ledger each periodic run.
