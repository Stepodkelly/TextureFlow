# TextureFlow

TextureFlow is a voice-first Android layer for notification attention and
**explicitly confirmed** actions (reply, snooze, dismiss).

## New here?

**Read [`START_HERE.md`](START_HERE.md) first.**  
It explains the folders in plain English.

Then use [`docs/CODE_MAP.md`](docs/CODE_MAP.md) when you need “which file?”

## Repo tour (short)

```text
START_HERE.md          ← begin here
app/                   ← Android phone app
convex/                ← cloud backend
texture-bridge/        ← voice/MCP bridge on a computer
intelligence/          ← urgency ranking
shared/                ← shared types
tools/                 ← test & demo scripts
docs/                  ← deep documentation
```

## Built surfaces

- Android notification listener with recovery, health job, durable outbox
- Android ↔ Convex connection with heartbeat, claims, and confirm-before-execute
- Voice bridge that can prepare/confirm proposals (phone executes)
- Convex core for devices, events, attention, proposals, commands, receipts
- Sensory cues (audio / haptic / visual)

Full architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Local configuration

Copy `.env.example` to `.env.local` and fill Convex values.  
Never put secrets in the Android APK.

## Verification

```sh
npm run check
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew lintDebug assembleDebug testDebugUnitTest
node --env-file=.env.local tools/live-smoke/run.mjs
node --env-file=.env.local tools/verify-bridge-live.mjs
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
