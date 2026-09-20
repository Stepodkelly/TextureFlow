# TextureFlow / Moth Market

Phone-first Android layer for notification attention and **explicitly confirmed**
actions (reply, snooze, dismiss), with the Moth Market chat UI.

**Convex and VoiceOS are stubbed** — the app runs on-device by default.

## New here?

**Read [`START_HERE.md`](START_HERE.md) first.**

Then use [`docs/CODE_MAP.md`](docs/CODE_MAP.md) when you need “which file?”

## Repo tour (short)

```text
START_HERE.md          ← begin here
app/                   ← Android phone app (primary)
convex/                ← cloud backend (STUBBED)
texture-bridge/        ← voice/MCP bridge (fixture-only stub)
intelligence/          ← urgency ranking (offline fallback)
shared/                ← shared types
tools/                 ← test & demo scripts
docs/                  ← deep documentation
```

## Built surfaces

- Android notification listener with lockout hardening (health policy, watchdog, force rebind)
- Local notification **bank** (adopt / arm / wake / outbound outbox)
- Local stub connection (no Convex required)
- Local confirm-before-execute for phone actions
- Sensory cues (audio / haptic / visual)
- Moth Market chat-list UI

Package map: [`app/README.md`](app/README.md) · “which file?”: [`docs/CODE_MAP.md`](docs/CODE_MAP.md)

## Local configuration

Copy `.env.example` to `.env.local` if you want. Convex values are optional while stubbed.

## Verification

```sh
npm run check
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew lintDebug assembleDebug testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
