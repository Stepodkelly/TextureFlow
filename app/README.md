# Android phone app (`app/`)

This is the code that runs **on the phone**.

## How to find things

Start at:

```text
app/src/main/java/com/textureflow/
```

Each folder is one job. Every folder has its own `README.md`.

| Folder | Job | Start reading |
|--------|-----|---------------|
| **`ui/`** | Moth Market screen, voice, confirm | `MainActivity.java` |
| **`notifications/`** | Capture + normalize + listener durability | `TextureNotificationListenerService.java` |
| **`bank/`** | Latest replyable notif per peer × app; arm/wake/outbox | `NotificationBank.java` |
| **`actions/`** | Reply / dismiss / snooze after confirm | `NotificationActionExecutor.java` |
| **`data/`** | SQLite + listener health | `TextureFlowDatabase.java` |
| **`policy/`** | Safety rules (“may we do this?”) | `CommandPolicy.java` |
| **`connection/`** | Local stub Core by default; optional Convex | `ConnectionMode.java` |
| **`texture/`** | Sounds + haptics + sensory cues | `TextureCueScheduler.java` |

## Other important paths

```text
app/src/main/AndroidManifest.xml   ← services, receivers, permissions
app/src/main/res/                  ← drawables, strings, Moth colors
app/src/main/assets/               ← images (e.g. moth texture)
app/src/test/java/com/textureflow/ ← unit tests (same package names)
app/build.gradle                   ← Android dependencies
```

## Mental model (local-first)

```text
Other apps post notifications
        ↓
notifications/   capture + normalize + health/rebind
        ↓
data/            save events locally
   ↘
bank/            adopt latest REPLY handle; snooze / wake / outbox
        ↓
ui/              Moth Market attention + voice confirm
        ↓
actions/         really reply / snooze / dismiss
        ↓
texture/         success / fail cues

connection/      optional cloud sync (stubbed by default)
```

## Build the app

From the repo root (needs Android SDK / Android Studio JDK):

```sh
./gradlew assembleDebug
./gradlew :app:installDebug   # with an emulator or device attached
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
