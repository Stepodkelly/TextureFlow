# `notifications/` — listen, normalize, stay alive

Captures notifications from WhatsApp, Telegram, Instagram, SMS, etc., and
keeps the Android `NotificationListenerService` from silently locking out.

## Read in this order

1. **Ingest** — `TextureNotificationListenerService` → `NotificationNormalizer` → `enqueueAttention`
2. **Wiring** — `NotificationRuntime` (stores, intelligence ledger, bank, executor, attention engine)
3. **Durability** — `ListenerHealthPolicy` → watchdog / health job / recovery

## Files by job

| Job | Files |
|-----|--------|
| Listener + dismiss/snooze | `TextureNotificationListenerService.java` |
| Clean fields + capabilities | `NotificationNormalizer.java`, `NormalizedNotification.java`, `NotificationSnapshot.java` |
| Fingerprints / versions | `ContentFingerprint.java`, `EventVersionPolicy.java` |
| Runtime wiring | `NotificationRuntime.java` (thin `enqueueAttention` hook) |
| Fresh vs locked-out rules | `ListenerHealthPolicy.java` |
| Rebind backoff | `NotificationRebindPolicy.java` |
| ~30s AlarmManager heartbeat | `NotificationWatchdogScheduler.java`, `NotificationWatchdogReceiver.java` |
| Periodic JobScheduler check + 14-day ledger purge | `NotificationHealthJobService.java` |
| Boot / package-replaced | `NotificationRecoveryReceiver.java` |

## Related

- `bank/` — adoptAndArm on REPLY posts (failures never poison health)
- `data/ListenerHealthStore.java` — durable connected / callback / reconcile timestamps
- `connection/TextureFlowConnectionService.java` — also runs an 8s listener health loop while FGS is up

## Tests

`app/src/test/java/com/textureflow/notifications/`
