# TextureFlow Android Notification Integration

This module implements Android's authoritative notification perception and action boundary. It uses only Android and Java platform APIs and supports `minSdk 26`.

## Guarantees

- Listener callbacks enqueue only lightweight work onto one background thread.
- Every event version and its upload outbox record commit in one SQLite transaction.
- `onListenerConnected()` enumerates active notifications, rebuilds live `PendingIntent` handles, and reconciles missing events.
- Callback bursts trigger a debounced active-state reconciliation, and a persisted health job repeats it every 15 minutes.
- Listener disconnect and process/package restart paths call `NotificationListenerService.requestRebind()` with bounded backoff.
- Content and action fingerprints prevent duplicate uploads while advancing the version when a notification or live action changes.
- Raw notification keys and `PendingIntent` objects remain on the phone.
- Reply, dismiss, and snooze require a matching trusted `ConfirmedProposal` and current event version.
- Command IDs and idempotency keys are reserved durably before external dispatch.
- A crash after dispatch but before receipt is treated as an uncertain failure and is never replayed automatically.
- A `DISPATCHED` receipt means Android accepted the action. It does not claim message delivery.

## Required Manifest Integration

Agent A intentionally did not edit `AndroidManifest.xml`. The integration owner must add the following declarations inside the existing manifest.

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
    <service
        android:name=".notifications.TextureNotificationListenerService"
        android:exported="true"
        android:label="TextureFlow notification access"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
        <intent-filter>
            <action android:name="android.service.notification.NotificationListenerService" />
        </intent-filter>
    </service>

    <service
        android:name=".notifications.NotificationHealthJobService"
        android:exported="false"
        android:permission="android.permission.BIND_JOB_SERVICE" />

    <receiver
        android:name=".notifications.NotificationRecoveryReceiver"
        android:enabled="true"
        android:exported="false">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        </intent-filter>
    </receiver>
</application>
```

No Gradle dependency is required.

## Permission and Setup Flow

The app cannot grant notification access itself. The setup UI should check `NotificationManagerCompat` only if AndroidX is later introduced; with platform-only APIs, read `Settings.Secure.getString(contentResolver, "enabled_notification_listeners")` or direct the user to:

```java
startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
```

Do not represent the listener as healthy until `ListenerHealthStore.read().connected` is true and a recent reconciliation timestamp exists.

## Cloud Transport Handoff

The Convex transport owns authentication, command claim, and network retries. It should obtain the local runtime and set the authenticated opaque owner ID once:

```java
NotificationRuntime runtime = NotificationRuntime.get(context);
runtime.configureOwner(authenticatedOwnerId);
String deviceId = runtime.getDeviceId();
```

Upload local mutations without blocking ingestion:

```java
for (OutboxRecord item : runtime.outbox().claimBatch(25, now, 30_000L)) {
    try {
        convexUpload(item.getKind(), item.getPayload());
        runtime.outbox().acknowledge(item.getId());
    } catch (Exception failure) {
        long retryAt = now + boundedBackoff(item.getAttempts());
        runtime.outbox().release(item.getId(), failure.toString(), retryAt);
    }
}
```

The transport must validate the shared JSON contract and authenticate ownership before constructing Java domain objects. After atomically claiming a Convex command, it must load the corresponding proposal in `CONFIRMED` or `COMMITTED` state and construct `ConfirmedProposal` from that trusted record, including its exact payload and status. The constructor rejects any other proposal state, and device policy compares the action payload to the confirmed payload. Never construct confirmation evidence from model text or a voice utterance alone.

Run execution on a background executor:

```java
ActionReceipt receipt = TextureNotificationListenerService.executeConfirmed(
        context,
        claimedCommand,
        confirmedProposal);
```

The receipt is already persisted with a `RECEIPT_COMPLETE` outbox record before this method returns. The transport uploads that outbox record; VoiceOS must wait for the uploaded receipt before announcing success.

## Local Storage

`textureflow-notifications.db` contains:

- `notification_events`: current normalized event and local-only Android key
- `pending_sync_operations`: lease-based event and receipt outbox
- `processed_commands`: durable idempotency reservation
- `action_receipts`: authoritative device results
- `listener_health`: connection, callback, reconciliation, and failure state

The current hackathon implementation stores notification bodies as plaintext SQLite data. Use only synthetic demo accounts. Before real-user testing, add encrypted-at-rest storage and retention/deletion controls without changing the event contract.

## Recovery Behavior

1. Listener connection performs a full active notification scan.
2. Each callback is serialized and followed by a debounced scan.
3. The health job requests a scan every 15 minutes.
4. Disconnect, boot, and package replacement request framework rebind.
5. Outbox leases expire, allowing interrupted uploads to be claimed again.
6. External Android actions do not retry after an uncertain crash boundary.

Android may delay jobs in Doze and may withhold callbacks when notification access is revoked. Reconciliation repairs durable state once access returns; no application can guarantee capture of a notification posted and removed entirely while its process and listener are unavailable.

## Device Proof Checklist

1. Add the manifest declarations and install a debug build.
2. Grant TextureFlow notification access in system settings.
3. Post a reply-capable notification from the selected demo messaging app.
4. Confirm `notification_events` contains version `1` and an `EVENT_UPSERT` outbox item.
5. Disable and re-enable notification access; confirm the event is recovered by active-state reconciliation.
6. Update the message notification; confirm the version advances and an old command returns `EVENT_CHANGED`.
7. Remove the notification; confirm a versioned `REMOVED` tombstone is queued.
8. Execute one confirmed reply; confirm the second phone receives it and a `DISPATCHED` receipt is queued.
9. Submit the same command again; confirm no second message is sent and the original receipt is returned.
10. Force-stop during a dispatch test; after restart, confirm TextureFlow refuses an uncertain replay.

## Known Platform Boundaries

- Notification listener access is user-controlled and can be revoked at any time.
- Some apps expose no free-form `RemoteInput`; TextureFlow reports `REPLY_NOT_SUPPORTED`.
- A source app can cancel or replace a `PendingIntent`; fingerprints and `CanceledException` handling fail safely.
- Work-profile and OEM notification policies can hide content or actions.
- `DISPATCHED` cannot prove server delivery or recipient receipt.
- `JobScheduler` periodic timing is inexact by design; callbacks and connection reconciliation remain the primary path.
