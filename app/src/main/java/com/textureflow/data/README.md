# `data/` — on-phone storage

SQLite / local stores so the app still works if the network is down.

| Important file | Role |
|----------------|------|
| `TextureFlowDatabase.java` | Database setup / migrations (`onUpgrade` chain; current `DATABASE_VERSION` is 2). `SqliteLedger` writes the v2 intelligence tables. |
| `NotificationRepository.java` | Saved notification events |
| `OutboxStore.java` | Cloud-sync outbox rows (≠ `bank/` messenger outbox) |
| `ListenerHealthStore.java` | Listener connected / callback / reconcile freshness |
| `ActionReceiptStore.java` | Action receipts |
| `DeviceIdentity.java` | This phone’s ids |

## Schema v2 (intelligence ledger)

`onCreate` builds the v1 notification schema, then `migrateTo2`. Existing v1 installs take the same step via `onUpgrade`.

| Table | Purpose |
|-------|---------|
| `attention_ticks` | Per-tick metrics. No message bodies. |
| `assessments` | Level + generated reason (`reason_text` ≤ 120). No bodies. |
| `role_runs` | Role timing / token / schema metrics. No bodies. |
| `proposals` | Bound drafts. `payload_text` allowed only until a terminal status. |
| `capability_profile` | Single-row JSON snapshot (doc §9.1 / §30). |
| `nodes`, `compute_sessions`, `depth_jobs`, `boundary_reports`, `tick_outcomes` | Reserved unused Part II tables (doc §34). |

Do not put messenger bank state here — that lives in `bank/` (SharedPreferences).
