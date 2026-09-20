# `notifications/` — listen to other apps

Captures notifications from WhatsApp, Telegram, Instagram, SMS, etc.

| Important file | Role |
|----------------|------|
| `TextureNotificationListenerService.java` | Android listener + dismiss/snooze hooks |
| `NotificationNormalizer.java` | Clean fields + capabilities (REPLY/SNOOZE/…) |
| `NotificationRuntime.java` | Wiring for listener runtime |
| `NotificationHealthJobService.java` | Periodic health / reconcile |
| `NotificationRecoveryReceiver.java` | Boot / package-replaced recovery |
