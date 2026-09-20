# Voice / MCP bridge (`texture-bridge/`)

This runs on a **computer**, not on the phone.

It lets a voice agent (or MCP client) **prepare and confirm** actions.
The phone still does the real reply/snooze/dismiss.

## Start here

| Path | What it is |
|------|------------|
| `src/server.ts` | Starts the bridge |
| `src/service.ts` | Tool logic |
| `src/adapters/convex.ts` | Live Convex backend |
| `src/adapters/fixture.ts` | Fake data for safe demos |
| `test/` | Automated tests |
| `examples/` | Example MCP config files |

## Beginner tip

For demos without risking real sends, use the **fixture** adapter first.
