# Code map — “Where do I look?”

Use this when you know **what you want to change** but not **which file**.

## Phone app (`app/src/main/java/com/textureflow/`)

Packages are one job each. Prefer the package `README.md` for a file list.

| I want to… | Look here |
|------------|-----------|
| Change the Moth Market chat UI / settings | `ui/MainActivity.java`, `ui/MothMarketTheme.java` |
| Change voice listening / speaking | `ui/ConversationalVoiceController.java` |
| Parse “reply with …” voice phrases | `ui/VoiceCommandParser.java` |
| Capture notifications from other apps | `notifications/TextureNotificationListenerService.java` |
| Harden / recover a locked-out listener | `notifications/ListenerHealthPolicy.java`, watchdog + health job |
| Turn raw Android notifications into events | `notifications/NotificationNormalizer.java` |
| Keep a snoozed replyable “bank” per peer × app | `bank/NotificationBank.java` (see `bank/README.md`) |
| Queue local outbound texts until REPLY is live | `bank/OutboundMessageOutbox.java` |
| Decide if reply/snooze is allowed | `policy/CommandPolicy.java` |
| Actually send a reply / snooze / dismiss | `actions/NotificationActionExecutor.java` |
| Keep live Reply buttons in memory | `actions/LiveActionRegistry.java` |
| Local-only Core (stubbed Convex) | `connection/` — start at `ConnectionMode.java` |
| Live Convex sync (optional, not default) | `connection/ConvexHttpGateway.java` |
| Store events / health on the phone | `data/` |
| Play haptic / audio cues | `texture/` |

### Suggested read order inside `app/`

```text
ui/  →  notifications/  →  bank/  →  actions/  →  data/  →  connection/
```

Tests mirror the same packages under `app/src/test/java/com/textureflow/`.

## Cloud (`convex/`) — stubbed / optional

| I want to… | Look here |
|------------|-----------|
| Database shape (tables) | `convex/schema.ts` |
| Devices online/offline | `convex/devices.ts` |
| Incoming notification events | `convex/events.ts` |
| What needs attention now | `convex/attention.ts` |
| Prepare / confirm an action | `convex/proposals.ts` |
| Commands waiting for the phone | `convex/commands.ts` |
| Proof the phone did it | `convex/receipts.ts` |
| Shared server helpers | `convex/lib/` |

## Voice bridge (`texture-bridge/`) — fixture stub

| I want to… | Look here |
|------------|-----------|
| MCP / HTTP server entry | `texture-bridge/src/server.ts` |
| Tool behavior | `texture-bridge/src/service.ts` |
| Talk to Convex | `texture-bridge/src/adapters/convex.ts` |
| Fake data for demos | `texture-bridge/src/adapters/fixture.ts` |

## Intelligence (`intelligence/`)

| I want to… | Look here |
|------------|-----------|
| Rank / prioritize events | `intelligence/src/priority.ts` |
| Safe fallback when AI is off | `intelligence/src/fallback.ts` |
| Public entry | `intelligence/src/index.ts` |

## Shared + tools

| I want to… | Look here |
|------------|-----------|
| Shared action/event types | `shared/contracts/domain.ts` |
| Run checks / demos | `tools/README.md` |

## Docs (deeper reading)

| Doc | Topic |
|-----|--------|
| [`START_HERE.md`](../START_HERE.md) | Beginner map of the whole repo |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Full system design (some Convex/VoiceOS sections are historical) |
| [`ANDROID_INTEGRATION.md`](ANDROID_INTEGRATION.md) | Notification listener details |
| [`ANDROID_CONNECTION.md`](ANDROID_CONNECTION.md) | Phone ↔ Convex connection |
| [`THREAT_MODEL.md`](THREAT_MODEL.md) | Security threats |
| [`TEST_PLAN.md`](TEST_PLAN.md) | How we test |
| [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) | Demo walkthrough |
