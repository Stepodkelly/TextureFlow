# Android phone app (`app/`)

This is the code that runs **on the phone**.

## How to find things (beginner tour)

Start at:

```text
app/src/main/java/com/textureflow/
```

Each folder is one job:

| Folder | Job (plain English) | Start reading |
|--------|---------------------|---------------|
| **`ui/`** | Screen, eye, voice, buttons | `ui/MainActivity.java` |
| **`notifications/`** | Listen to other apps’ notifications | `TextureNotificationListenerService.java` |
| **`actions/`** | Reply / dismiss / snooze once confirmed | `NotificationActionExecutor.java` |
| **`connection/`** | Talk to Convex cloud safely | `TextureFlowConnectionService.java` |
| **`data/`** | Local phone database (SQLite) | `TextureFlowDatabase.java` |
| **`policy/`** | Safety rules (“may we do this?”) | `CommandPolicy.java` |
| **`texture/`** | Sounds + haptics + sensory cues | `TextureCueScheduler.java` |
| **`bank/`** | *(Planned)* paired outbound “notification bank” | `bank/README.md` |

## Other important paths

```text
app/src/main/AndroidManifest.xml   ← declares app, services, permissions
app/src/main/res/                  ← icons, strings, styles
app/src/main/assets/               ← images (e.g. moth texture)
app/src/test/java/...              ← unit tests (mirror the same folders)
app/build.gradle                   ← Android dependencies
```

## Mental model

```text
Other apps post notifications
        ↓
notifications/  (capture + normalize)
        ↓
data/           (save locally)
        ↓
connection/     (sync to cloud when online)
        ↓
ui/             (show attention + voice)
        ↓
user confirms
        ↓
actions/        (really reply / snooze / dismiss)
        ↓
texture/        (play success/fail cues)
```

## Build the app

From the repo root (needs Android SDK / Android Studio JDK):

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
