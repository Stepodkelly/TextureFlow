# TextureFlow e2e (`tools/e2e`)

Local instrumentation against an **emulator or a USB phone**. There is no GitHub Actions emulator job.

Unit tests stay on `./tools/test-android.sh`. This script is device-only.

## Run

From the repo root:

```bash
./tools/e2e/run.sh
```

Optional:

```bash
./tools/e2e/run.sh --reinstall
./tools/e2e/run.sh --serial emulator-5554
```

The harness:

1. Finds `ANDROID_HOME` / `adb` (`ANDROID_SDK_ROOT`, `local.properties` `sdk.dir`, or `~/Library/Android/sdk`)
2. Grants the listener:
   `adb shell cmd notification allow_listener com.textureflow/com.textureflow.notifications.TextureNotificationListenerService`
3. Installs debug + androidTest APKs if they are missing (`--reinstall` forces it)
4. Runs `com.textureflow.e2e.TextureFlowE2eTest` only (not Stream E's model bench)

## Tests

| Class | Role |
|---|---|
| `com.textureflow.e2e.TextureFlowE2eTest` | Scenarios |
| `com.textureflow.e2e.FakeMessagingNotifier` | Posts MessagingStyle + RemoteInput from the **test** process |

Device-observation methods skip with `assumeTrue` when the notification listener is off or nothing is stored. They do not fail `./tools/test-android.sh`.

## Limits

- No real WhatsApp or SMS send. Reply confirm is a `CommandPolicy` / `ConfirmedProposal` dry-run.
- Ranking uses the deterministic triage path. Stored levels are checked only when the listener actually ingested the fake notification.
- Own-app notifications are ignored by `NotificationIngestionPolicy`. The fake notifier posts from `com.textureflow.test`.
- Stream G engine invalidation is not required: canceling a posted notification must leave no live event.
