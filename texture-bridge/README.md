# Voice / MCP bridge (`texture-bridge/`)

**Stubbed:** this bridge no longer talks to Convex or VoiceOS by default.
It always uses the **fixture** adapter so demos and tests stay local.

You can delete this package later if you do not need an MCP/voice host.

## Start here

| Path | What it is |
|------|------------|
| `src/server.ts` | Starts the bridge |
| `src/service.ts` | Tool logic |
| `src/adapters/fixture.ts` | Local fake data (active) |
| `src/adapters/convex.ts` | Live Convex adapter (unused while stubbed) |
| `test/` | Automated tests |
