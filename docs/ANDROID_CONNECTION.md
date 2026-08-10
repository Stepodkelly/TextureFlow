# TextureFlow Android Connection Sidecar

The `com.textureflow.connection` package is a bounded Android-to-Convex transport. It does not
start itself and this change intentionally does not edit the manifest, activity, Gradle files, or
notification subsystem.

## What It Does

- Registers the existing durable Android device identity with Convex.
- Heartbeats every 25 seconds, below Core's 45-second stale-device threshold.
- Claims the existing SQLite notification outbox with leases and acknowledges only after Convex
  reports mutation success.
- Retries event and receipt delivery with capped exponential backoff and jitter.
- Polls `commands:forDevice`, persists a stable claim token before claiming, loads trusted
  `proposals:get` evidence, calls `commands:startExecution`, and invokes
  `TextureNotificationListenerService.executeConfirmed` through `ConfirmedActionExecutor`.
- Uploads the locally durable receipt with the original command trace and claim token.
- Runs the serialized transport loop and watchdog on separate executors. Every scheduled task
  catches failures and reschedules itself; a stale or wedged loop is cancelled and replaced.
- Returns `START_STICKY` from its foreground service so Android can recreate it after process loss.

## Delivery Guarantees

`NotificationRepository` and `ActionReceiptStore` already commit data and outbox rows in the same
SQLite transaction. The sidecar preserves those guarantees:

1. A row becomes `IN_FLIGHT` under a finite lease.
2. Convex receives an idempotent event version or receipt ID.
3. The local row is deleted only after a successful Convex response.
4. If the process dies before local acknowledgement, lease expiry makes the row claimable again.
5. If delivery fails, the row returns to `PENDING` with a bounded retry time.

Receipt claims are retained in private runtime storage until the corresponding receipt outbox row
is acknowledged. Losing that claim storage intentionally fails closed rather than attempting an
action or submitting a receipt under a new token.

The platform still cannot capture a notification that appears and disappears entirely while
notification access and the listener process are unavailable. Active-state reconciliation in the
notification module remains the recovery mechanism for notifications that still exist.

## Required Manifest Integration

Add these permissions to the manifest. Android 13 notification permission should be requested in
the existing setup UI; foreground operation is still surfaced by the system when that permission
is denied.

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING" />
```

Add this service inside `<application>`:

```xml
<service
    android:name=".connection.TextureFlowConnectionService"
    android:exported="false"
    android:foregroundServiceType="remoteMessaging" />
```

The app already declares `android.permission.INTERNET`.

## Configuration

Provision credentials through an authenticated enrollment flow and then write them to private
runtime storage. Do not put a token literal in source, resources, logs, intents, saved state, or the
manifest.

```java
TextureFlowConnectionController.configure(
        context,
        enrollment.convexUrl(),
        enrollment.ownerId(),
        enrollment.deviceActorToken(), // null when using OIDC
        enrollment.oidcToken(),        // null when using the demo device token
        "Stephen's Pixel");
```

`ConnectionConfigStore` can also reflect these optional generated
`BuildConfig` fields:

- `CONVEX_URL`
- `TEXTUREFLOW_OWNER_ID`
- `TEXTUREFLOW_DEVICE_TOKEN`
- `TEXTUREFLOW_OIDC_TOKEN`
- `TEXTUREFLOW_DEVICE_NAME`

Runtime values override `BuildConfig`. The current build leaves both token fields empty and only
pre-fills the non-secret deployment URL and owner ID. The setup dialog writes the scoped device
credential as AES-GCM ciphertext using an Android Keystore key. Production enrollment should
still issue a per-device, revocable credential instead of the shared hackathon demo role token.

Call `ConnectionConfigStore.clearSecrets(context)` on sign-out or credential revocation.

## Starting And Stopping

Start only after enrollment succeeds and the user has opted into the persistent connection:

```java
TextureFlowConnectionController.start(context);
```

Stop on sign-out or when the user disables connection mode:

```java
TextureFlowConnectionController.stop(context);
ConnectionConfigStore.clearSecrets(context);
```

To resume after reboot, the existing boot receiver can call the controller only when persisted
enrollment and user opt-in are both present. The controller uses `startForegroundService` on API
26 and later; `TextureFlowConnectionService` promotes itself immediately.

## Wire Mapping

| Local operation | Convex function |
| --- | --- |
| Device startup | `devices:register` |
| Availability | `devices:heartbeat` |
| Active/updated event | `events:upsert` |
| Removed event | `events:markRemoved` |
| Command feed | `commands:forDevice` |
| Exclusive claim | `commands:claim` |
| Confirmation evidence | `proposals:get` |
| Execution boundary | `commands:startExecution` |
| Device evidence | `receipts:complete` |

All calls use the exact Core `actor` shape with role `DEVICE`, owner ID, device ID, and either a
demo device token or OIDC bearer identity. The HTTP adapter never logs headers, actor payloads,
notification bodies, reply text, or response bodies.

## Recovery Timing

- Healthy command poll: 1.5 seconds.
- Device heartbeat: 25 seconds.
- HTTP connect timeout: 8 seconds.
- HTTP read timeout: 10 seconds.
- Poll watchdog deadline: 20 seconds.
- Connection retry: 1 second to 60 seconds with 20 percent jitter.
- Outbox retry: 1 second to 5 minutes with 20 percent jitter.
- Outbox lease: 2 minutes for batches of at most 8 rows.

The watchdog runs on its own executor, rate-limits recovery, and replaces the loop when an
operation exceeds the deadline or scheduled polling stops making progress. Remote mutations and
local command execution remain idempotent if an old network call returns after replacement.

This watchdog detects failures while the process is allowed to run. It cannot override user
force-stop, revoked permissions, Doze network suspension, battery restrictions, or OEM process
policy. `START_STICKY`, the foreground notification, boot integration, finite HTTP timeouts, and
the durable outbox provide recovery when Android allows execution again; the UI should still show
heartbeat age rather than claiming uninterrupted reachability.

## Verification

Pure JVM tests cover state transitions, backoff bounds, outbox acknowledgement and lease recovery,
watchdog restart decisions, and exact command/proposal payload matching:

```bash
./gradlew testDebugUnitTest
```

Device integration should additionally verify process kill/restart, airplane-mode recovery,
notification permission revocation, command claim replay, and one real `RemoteInput` reply with a
Convex receipt before the voice layer announces success.

## Changed Files

Production source:

- `AndroidConfirmedActionExecutor.java`
- `AndroidDurableOutbox.java`
- `BackoffPolicy.java`
- `ClaimRecord.java`
- `ClaimStore.java`
- `CommandEnvelopeValidator.java`
- `CommandProcessor.java`
- `ConfirmedActionExecutor.java`
- `ConnectionClock.java`
- `ConnectionConfig.java`
- `ConnectionConfigStore.java`
- `ConnectionEngine.java`
- `ConnectionEngineFactory.java`
- `ConnectionIds.java`
- `ConnectionObserver.java`
- `ConnectionStateMachine.java`
- `ConnectionWatchdog.java`
- `ConvexHttpGateway.java`
- `ConvexOutboxDelivery.java`
- `DurableOutbox.java`
- `OutboxCoordinator.java`
- `OutboxDelivery.java`
- `RemoteCommand.java`
- `RemoteGateway.java`
- `RemoteProposal.java`
- `SharedPreferencesClaimStore.java`
- `TextureFlowConnectionController.java`
- `TextureFlowConnectionService.java`

JVM tests:

- `BackoffPolicyTest.java`
- `CommandEnvelopeValidatorTest.java`
- `ConnectionStateMachineTest.java`
- `ConnectionWatchdogTest.java`
- `OutboxCoordinatorTest.java`

Documentation:

- `docs/ANDROID_CONNECTION.md`
