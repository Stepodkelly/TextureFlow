# `intelligence/ledger/` — attention metrics store

Append-only metrics for ticks, assessments, role runs, and proposals.
Tick / assessment / role rows never store message bodies. Proposal
payload text is kept only until the proposal reaches a terminal status.

| File | Role |
|------|------|
| `IntelligenceLedger.java` | Write API: ticks, assessments, role runs, proposals, capability profile, 14-day purge |
| `InMemoryLedger.java` | Full fake for other streams' JVM unit tests. No `android.*` imports. |
| Record types | `TickRecord`, `AssessmentRecord`, `RoleRunRecord`, `ProposalRecord` |

Retention is 14 days (`IntelligenceLedger.RETENTION_MILLIS`). Call
`purgeOlderThan(now - RETENTION_MILLIS)` from
`NotificationHealthJobService` or app start — those files are not owned
here.

`SqliteLedger` is a Wave 1 leftover for a device. Schema v2 is already
on `TextureFlowDatabase`; wire the SQLite implementation when an
instrumented/device run is available.
