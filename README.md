# TextureFlow

TextureFlow is a voice-first Android accessibility layer for notification attention and explicitly
confirmed actions. The interface combines an Eye of Horus identity, a dense moth-paper texture,
liquid-glass controls, semantic audio cues, and haptic boundaries.

## Built Surfaces

- Android notification listener with active-state reconciliation, boot/package recovery, a
  15-minute health job, durable SQLite outbox, and bounded rebind backoff.
- Android-to-Convex foreground connection with heartbeat, command polling, an independent
  watchdog, finite network timeouts, persisted command claims, and lease-safe outbox delivery.
- Confirmed action boundary for reply, dismiss, and snooze. The voice bridge can prepare and
  confirm a proposal, but only Android can execute a live `PendingIntent` and create its receipt.
- Convex Core for devices, events, attention, people, sessions, proposals, confirmation grants,
  commands, receipts, fixtures, and correlated traces.
- VoiceOS-compatible MCP bridge with eleven tools and no immediate-send operation.
- Deterministic intelligence fallback, bounded context, strict model schemas, rehearsal fixtures,
  safety scenarios, and a non-dispatching live smoke harness.
- Sensory engine with carpet scrolling, glass focus, boundary bumps, urgency, confirmation,
  execution, success, and failure cues across audio, haptic, and visual channels.

The full target architecture is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Android recovery
details are in [docs/ANDROID_INTEGRATION.md](docs/ANDROID_INTEGRATION.md) and
[docs/ANDROID_CONNECTION.md](docs/ANDROID_CONNECTION.md).

## Local Configuration

Copy `.env.example` to `.env.local` and provide the Convex deployment values. Both `.env.local`
and `.env.convex` are ignored. Team/deploy credentials are used only by local tooling and must
never be placed in Android.

The Android debug build pre-fills only the non-secret Convex URL and owner ID. Use **Configure Core
link** in the app to enroll the scoped device token at runtime. TextureFlow encrypts that token
with an Android Keystore AES-GCM key; it is not packaged in the APK.

## Verification

```sh
npm run check
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew lintDebug assembleDebug testDebugUnitTest
node --env-file=.env.local tools/live-smoke/run.mjs
node --env-file=.env.local tools/verify-bridge-live.mjs
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Demo Safety

`tools/live-smoke/run.mjs` uses synthetic data and deliberately ends execution with a
`POLICY_BLOCKED` receipt. Rehearsal commands never create an Android execution receipt. The bridge
only announces success after a receipt from the authenticated target device says `DISPATCHED`.
