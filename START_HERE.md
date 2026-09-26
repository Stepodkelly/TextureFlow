# Start here (beginner map)

TextureFlow / Moth Market is primarily the **Android phone app**.

**Convex and VoiceOS are stubbed** so you can push the product without cloud or
a voice host. Those folders remain in the tree for now and can be deleted later.

## The big picture (current)

```text
YOUR PHONE
───────────
app/          Android phone app
              notifications/  capture + lockout recovery
              bank/           replyable notification bank
              ui/             Moth Market chat UI
              actions/        confirmed reply / snooze / dismiss

OPTIONAL / STUBBED (safe to ignore for now)
──────────
convex/           cloud backend (stubbed)
texture-bridge/   VoiceOS MCP bridge (fixture-only)
intelligence/     TS ranking oracle (Java engine lives in app/)
shared/           shared types
tools/            scripts (live Convex checks skip without credentials)
docs/             architecture notes (partly historical)
```

## What each top-level folder is for

| Folder | Plain English | When you open it |
|--------|---------------|------------------|
| **`app/`** | The Android phone app (what you install) | UI, notifications, on-device intelligence, bank, replies — see `app/README.md` |
| **`convex/`** | Cloud backend — **stubbed / ignore for now** | Only if restoring live sync later |
| **`texture-bridge/`** | Voice/MCP bridge — **fixture-only stub** | Only if restoring VoiceOS later |
| **`intelligence/`** | TypeScript ranking oracle + evals | Golden parity for the Java engine in `app/` |
| **`shared/`** | Shared contracts used by several parts | Types that must match everywhere |
| **`tools/`** | Scripts to test and demo | Running checks, smoke tests |
| **`docs/`** | Longer explanations | Deep reading after this map |

## How to run tests

From the repo root:

```sh
npm run check                 # TypeScript contracts, rehearsal, bridge, intelligence
./tools/test-android.sh       # Android unit tests (sets JAVA_HOME to Android Studio if needed)
```

CI runs both on every push to `main` and on pull requests (`.github/workflows/ci.yml`).

## Suggested reading order

1. This file (`START_HERE.md`)
2. [`app/README.md`](app/README.md) — phone app map
3. [`docs/CODE_MAP.md`](docs/CODE_MAP.md) — “where does feature X live?”
4. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — full system (advanced; some Convex/VoiceOS sections are historical)

## One sentence per major idea

- **Notification** lands on the phone → TextureFlow **listens** and stores a safe copy.
- **Attention** = “what should I look at now?”
- **Proposal** = “I want to reply/snooze/dismiss” (not done yet).
- **Confirm** = human says yes.
- **Receipt** = phone actually did it (or blocked it).

## Do not edit (usually)

- `node_modules/`, `**/build/`, `.gradle/` — generated
- `.env.local`, `.env.convex` — secrets (use `.env.example` as the template)
- `convex/_generated/` — created by Convex tooling (unused while stubbed)
