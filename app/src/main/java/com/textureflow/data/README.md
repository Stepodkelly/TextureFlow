# `data/` — on-phone storage

SQLite / local stores so the app still works if the network is down.

| Important file | Role |
|----------------|------|
| `TextureFlowDatabase.java` | Database setup / migrations (`onUpgrade` chain; v2 reserved for Stream C) |
| `NotificationRepository.java` | Saved notification events |
| `OutboxStore.java` | Cloud-sync outbox rows (≠ `bank/` messenger outbox) |
| `ListenerHealthStore.java` | Listener connected / callback / reconcile freshness |
| `ActionReceiptStore.java` | Action receipts |
| `DeviceIdentity.java` | This phone’s ids |

Do not put messenger bank state here — that lives in `bank/` (SharedPreferences).
