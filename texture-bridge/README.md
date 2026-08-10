# TextureFlow VoiceOS Bridge

The bridge is a TypeScript MCP server with unchanged stdio and stateful Streamable
HTTP entry points. It gives VoiceOS a bounded, speech-friendly view of TextureFlow
Core. It can read attention and prepare, revise, confirm, or cancel proposals. It
cannot execute Android actions directly.

The safety boundary is fixed:

```text
proposal -> exact spoken preview -> confirmation -> command -> Android receipt
```

There is deliberately no immediate-send or generic execution tool. A successful
confirmation response says an action was dispatched only when a validated device
receipt proves `DISPATCHED`. If the receipt does not arrive during the bounded
wait, the bridge says the command is queued and still awaiting the phone.

## Quick start

```bash
cd texture-bridge
npm install
npm run check
npm run dev
```

`npm run dev` starts deterministic `REHEARSAL` mode by default. The process uses
stdout exclusively for MCP protocol traffic. Structured operational logs are
written to stderr and never include message bodies or reply text.

## Streamable HTTP

```bash
npm run dev:http
# or, after npm run build, load the ignored root environment file:
./scripts/run-live-http.sh
```

The listener defaults to `http://127.0.0.1:8787/mcp`; health is available at
`http://127.0.0.1:8787/healthz`. Override the listener with
`TEXTUREFLOW_HTTP_HOST` and `TEXTUREFLOW_HTTP_PORT`. Each initialized HTTP session
owns a separate MCP server, bridge service, and speech-reference store. Clients
should terminate sessions with `DELETE /mcp` when finished.

## VoiceOS launch

1. Run `npm run build`.
2. Copy the shape of `examples/voiceos.mcp.json` into VoiceOS's custom MCP
   configuration.
3. Replace the example script path with the absolute path to `dist/index.js`.
4. Restart or reconnect the VoiceOS MCP integration.

`examples/voiceos.convex.mcp.json` shows the live adapter variables. The bridge
reads configuration from the launch environment and does not load dotenv files.

## Tools

| Tool | Behavior |
| --- | --- |
| `texture_status` | Reports mode, bridge/device state, and active counts |
| `texture_list_attention` | Lists up to ten ranked active events |
| `texture_read_event` | Reads one event or recent event reference |
| `texture_messages_from` | Reads active messages for a person |
| `texture_person_context` | Returns bounded cross-app person context |
| `texture_prepare_reply` | Creates an exact reply proposal |
| `texture_revise_reply` | Revises the active reply and requires confirmation again |
| `texture_prepare_dismiss` | Proposes notification dismissal |
| `texture_prepare_snooze` | Proposes bounded notification snooze |
| `texture_confirm_action` | Atomically confirms and waits for a device receipt |
| `texture_cancel_action` | Cancels without creating a command |

Every input is parsed with a strict Zod schema. Unknown fields, empty identifiers,
oversized text, invalid session IDs, and snooze values outside 1-1440 minutes are
rejected before reaching the backend.

## Session references

VoiceOS may pass an explicit `session_id`. When omitted, the bridge uses
`voiceos-default`. Presented event, person, and proposal references live only in
that session and expire after two minutes by default. This supports phrases such
as `that one` and confirmation without restating a proposal ID, while preventing
references from leaking across sessions.

Change the lifetime with `TEXTUREFLOW_SESSION_TTL_MS`. Confirmation still obeys
the shorter proposal expiry enforced by TextureFlow Core.

## Tool envelope

MCP text content contains only the speech-ready summary. The complete result is
also returned as structured content:

```json
{
  "ok": true,
  "data": {},
  "spokenSummary": "Reply to Sam on WhatsApp: I'm coming down. Say confirm to authorize this action, or cancel.",
  "requiresConfirmation": true,
  "proposalId": "prop_456",
  "textureCue": "CONFIRMATION_REQUIRED",
  "traceId": "trace_abc"
}
```

The `textureCue` is descriptive state for synchronized clients. The bridge does
not force Android to render sound, haptics, or visuals.

## Runtime configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `TEXTUREFLOW_ADAPTER` | `fixture` | `fixture` or `convex` |
| `TEXTUREFLOW_OWNER_ID` | `demo-owner` | Owner included in every Core request |
| `TEXTUREFLOW_SESSION_TTL_MS` | `120000` | Voice reference lifetime |
| `CONVEX_URL` | none | Required live deployment URL |
| `CONVEX_AUTH_TOKEN` | none | Optional runtime identity token passed to Convex |
| `TEXTUREFLOW_RECEIPT_TIMEOUT_MS` | `15000` | Bounded receipt wait after confirmation |
| `TEXTUREFLOW_HTTP_HOST` | `127.0.0.1` | Streamable HTTP bind address |
| `TEXTUREFLOW_HTTP_PORT` | `8787` | Streamable HTTP bind port |

Do not place deployment or identity credentials in the VoiceOS example files.
Supply them through the local launch environment or the team's ignored secret
file and process launcher.

## Convex adapter contract

The adapter uses function references without depending on generated Convex files,
which keeps this package independently buildable during the parallel hackathon
work. Agent B/Core should expose these names and return values that validate
against `shared/contracts/domain.ts`:

| Function | Kind | Bridge input beyond owner/session/trace | Expected output |
| --- | --- | --- | --- |
| `devices:status` | query | none | `BridgeStatus` |
| `attention:list` | query | `limit` | `NotificationEvent[]` |
| `events:get` | query | `eventId` | `NotificationEvent` |
| `people:messagesFrom` | query | `personName` | `NotificationEvent[]` |
| `people:context` | query | `personName` | `PersonContext` |
| `proposals:create` | mutation | `eventId`, `actionType`, `payload` | `ActionProposal` |
| `proposals:revise` | mutation | `proposalId`, `payload` | `ActionProposal` |
| `proposals:cancel` | mutation | `proposalId` | `ActionProposal` |
| `proposals:confirm` | mutation | `proposalId` | `{ proposal, command, event, receipt? }` |
| `receipts:getByCommand` | query | `commandId` | `ActionReceipt` or `null` |

`proposals:confirm` must atomically create the confirmation grant and command. It
must never execute the phone action itself. The Android command subscriber owns
execution and writes the authoritative receipt.

## Verification

```bash
npm run typecheck
npm run test
npm run build
```

The tests cover strict inputs, session isolation/expiry, fixture determinism,
revision, cancellation, stale event versions, duplicate confirmation, Convex
boundary validation, and truthful receipt language.
