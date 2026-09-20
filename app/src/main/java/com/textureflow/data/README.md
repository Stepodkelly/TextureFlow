# `data/` — on-phone storage

SQLite / local stores so the app still works if the network is down.

| Important file | Role |
|----------------|------|
| `TextureFlowDatabase.java` | Database setup |
| `NotificationRepository.java` | Saved notification events |
| `OutboxStore.java` | Local cloud-sync outbox rows |
| `DeviceIdentity.java` | This phone’s ids |
