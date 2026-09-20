# Code map — “Where do I look?”

Use this when you know **what you want to change** but not **which file**.

## Phone app (`app/`)

| I want to… | Look here |
|------------|-----------|
| Change the main screen / eye / reply panel | `app/.../ui/MainActivity.java` |
| Change voice listening / speaking | `app/.../ui/ConversationalVoiceController.java` |
| Parse “reply with …” voice phrases | `app/.../ui/VoiceCommandParser.java` |
| Read notifications from WhatsApp/Telegram/etc. | `app/.../notifications/TextureNotificationListenerService.java` |
| Turn raw Android notifications into clean events | `app/.../notifications/NotificationNormalizer.java` |
| Decide if reply/snooze is allowed | `app/.../policy/CommandPolicy.java` |
| Actually send a reply / snooze / dismiss | `app/.../actions/NotificationActionExecutor.java` |
| Keep live Reply buttons in memory | `app/.../actions/LiveActionRegistry.java` |
| Connect to Convex / sync outbox | `app/.../connection/` |
| Store events on the phone | `app/.../data/` |
| Play haptic / audio cues | `app/.../texture/` |
| Future: keep a snoozed “bank” to start chats | `app/.../bank/` (planned) |

## Cloud (`convex/`)

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

## Voice bridge (`texture-bridge/`)

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
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Full system design |
| [`ANDROID_INTEGRATION.md`](ANDROID_INTEGRATION.md) | Notification listener details |
| [`ANDROID_CONNECTION.md`](ANDROID_CONNECTION.md) | Phone ↔ Convex connection |
| [`THREAT_MODEL.md`](THREAT_MODEL.md) | Security threats |
| [`TEST_PLAN.md`](TEST_PLAN.md) | How we test |
| [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) | Demo walkthrough |
