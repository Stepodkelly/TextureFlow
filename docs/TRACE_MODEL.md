# TextureFlow Trace Model

## Purpose

The trace must make a distributed voice action legible without turning telemetry
into authority. It answers where an event traveled, how long each boundary took,
and why a command stopped. It does not prove that Android acted unless an
authenticated Android receipt is attached to the matching command.

The presentation has three evidence classes:

| Class | Meaning | Allowed claim |
| --- | --- | --- |
| `REHEARSAL` | Deterministic synthetic fixture generated offline | Contract/state behavior only |
| `LIVE_COORDINATION` | Authenticated event observed in Bridge/Core transport | Requested, proposed, confirmed, queued, or claimed |
| `DEVICE_EVIDENCE` | Receipt written by the authenticated target Android device | `DISPATCHED`, `FAILED`, `EXPIRED`, or `STALE` exactly as recorded |

An imported JSON file is unverified evidence even if its shape validates. The
offline trace renderer therefore labels raw input and never upgrades it to
`DEVICE_EVIDENCE`.

## Logical trace event

`TraceEvent` is not yet part of the shared contract. Until the lead adds it, each
runtime should emit the following logical shape without creating a conflicting
shared type:

```json
{
  "mode": "LIVE",
  "traceId": "trace_abc",
  "spanId": "span_android_dispatch",
  "parentSpanId": "span_command_claim",
  "sequence": 10,
  "name": "ACTION_DISPATCHED",
  "service": "ANDROID_MOBILE",
  "outcome": "OK",
  "occurredAt": "2026-08-09T18:10:11.000-07:00",
  "durationMs": 81,
  "correlation": {
    "eventId": "evt_123",
    "eventVersion": 2,
    "proposalId": "prop_456",
    "commandId": "cmd_789",
    "deviceId": "pixel_1",
    "sessionId": "voice_session_7"
  },
  "attributes": {
    "actionType": "REPLY",
    "sourceApp": "com.whatsapp"
  }
}
```

Required fields are `mode`, `traceId`, `spanId`, `name`, `service`, `outcome`,
and `occurredAt`. Correlation IDs are included only when known. Attributes must
be allowlisted and redacted.

Allowed values:

```text
mode:       LIVE | REHEARSAL
service:    ANDROID_MOBILE | TEXTUREFLOW_CORE | TEXTUREFLOW_BRIDGE
            | TEXTURE_ENGINE | INTELLIGENCE | QA_HARNESS
outcome:    OK | ERROR | SKIPPED | TIMEOUT
```

## Propagation

1. Android creates `traceId` when it first observes a notification.
2. Event upsert carries the trace ID to Core without raw Android notification
   keys or action handles.
3. Bridge creates a child span for every MCP tool call and carries the same
   trace ID through proposal operations.
4. Core stores the trace ID on proposal and command records.
5. Android command claim continues the trace and includes the exact same trace
   ID in `ActionReceipt`.
6. Receipt synchronization and cue rendering create child spans correlated to
   the command and receipt.
7. A conversation covering multiple events uses one voice-session trace with
   links to each event trace; it does not rewrite original trace ancestry.

If propagation is missing, create a new trace and record a `TRACE_LINK_MISSING`
diagnostic. Never guess an identifier from names, timestamps, or message text.

## Canonical events

### Android perception

```text
LISTENER_CONNECTED
ACTIVE_SNAPSHOT_STARTED
ACTIVE_SNAPSHOT_RECONCILED
LISTENER_DISCONNECTED
EVENT_RECEIVED
EVENT_UPDATED
EVENT_REMOVED
EVENT_STORED_LOCAL
OUTBOX_ENQUEUED
EVENT_SYNCED
ACTION_HANDLE_REGISTERED
ACTION_HANDLE_REMOVED
```

### Voice and Core

```text
VOICE_TOOL_CALLED
ATTENTION_LISTED
PROPOSAL_CREATED
PROPOSAL_REVISED
PROPOSAL_CANCELLED
PROPOSAL_STALE
PROPOSAL_CONFIRMED
CONFIRMATION_REJECTED
COMMAND_QUEUED
COMMAND_CLAIMED
COMMAND_EXPIRED
```

### Android execution and sensory result

```text
POLICY_VALIDATED
ACTION_EXECUTION_STARTED
ACTION_DISPATCHED
ACTION_FAILED
RECEIPT_STORED_LOCAL
RECEIPT_SYNCED
CUE_SCHEDULED
CUE_RENDERED
CUE_SUPPRESSED_FOR_SPEECH
```

### Rehearsal-only

```text
REHEARSAL_STARTED
EVENT_INJECTED
UNTRUSTED_CONTENT_QUARANTINED
DUPLICATE_CONFIRMATION_IGNORED
DEVICE_EXECUTION_NOT_ATTEMPTED
REHEARSAL_COMPLETE
```

`ACTION_DISPATCHED`, `RECEIPT_STORED_LOCAL`, and `RECEIPT_SYNCED` are forbidden
in Agent F rehearsal output.

## Receipt proof rule

The live trace surface may render a green execution milestone only when all are
true:

1. The receipt came through an authenticated Core mutation assigned to the
   target device.
2. `receipt.commandId` matches the immutable command.
3. `receipt.deviceId` matches `command.targetDeviceId` and owner.
4. `receipt.traceId` matches the command trace.
5. The command was previously claimed by that device and was unexpired.
6. Receipt status is one of the shared contract values.
7. Core enforces one terminal receipt identity per command.

`CommandStatus.DISPATCHED`, a Bridge timeout fallback, a model sentence, trace
event text, and second-phone visual observation are not substitutes for this
receipt. The second phone is excellent demo corroboration but is not the system
of record.

## Judge-facing presentation

Use one compact horizontal or vertical timeline with five lanes:

```text
PHONE OBSERVES -> CORE ORGANIZES -> VOICE PROPOSES
-> USER CONFIRMS -> PHONE EXECUTES
```

Presentation rules:

- Top-left badge is always `LIVE` or high-contrast `REHEARSAL`.
- Show human labels first and technical IDs in an expandable detail view.
- Keep the current stage, elapsed time, and terminal result visible.
- Use neutral states for observed/queued/claimed, green only for a proven
  dispatched receipt, amber for waiting/stale, and red plus text for failure.
- Never show notification body, reply body, contact address, credentials, raw
  Android key, or `PendingIntent` in the public timeline.
- Display `Dispatched through WhatsApp`, not `Delivered`, for a dispatched
  receipt.
- A missing receipt remains `Awaiting phone evidence` and times out visibly.
- Rehearsal trace ends with `Android execution not attempted` and has no receipt
  card.

The timeline should help judges understand the architecture in five seconds;
the raw span table is for operator diagnosis.

## Metrics

Derive latency from same-trace timestamps:

```text
notification_to_core = EVENT_SYNCED - EVENT_RECEIVED
voice_tool_latency    = tool response - VOICE_TOOL_CALLED
confirm_to_claim      = COMMAND_CLAIMED - PROPOSAL_CONFIRMED
claim_to_receipt      = RECEIPT_STORED_LOCAL - COMMAND_CLAIMED
receipt_sync_latency  = RECEIPT_SYNCED - RECEIPT_STORED_LOCAL
cue_render_latency    = CUE_RENDERED - CUE_SCHEDULED
reconcile_latency     = ACTIVE_SNAPSHOT_RECONCILED - ACTIVE_SNAPSHOT_STARTED
```

Counters and gauges:

```text
listener_disconnect_total
listener_reconcile_failure_total
notification_deduplicated_total
notification_outbox_depth
command_duplicate_total
command_expired_total
receipt_upload_retry_total
cue_suppressed_for_speech_total
device_heartbeat_age_ms
```

Do not compute cross-device latency unless clocks are synchronized and the
measurement labels the uncertainty. Prefer server receive times around network
boundaries and monotonic clocks for durations within one process.

## Privacy and retention

Allowlist only:

- Opaque IDs and versions
- Package identifier, action type, status, error code
- Counts, durations, app/build version, network category
- Listener health, capability names, and sensory profile name

Never log:

- Notification or reply bodies
- Contact phone numbers, email addresses, usernames, or raw display names
- OAuth/API secrets or headers
- Raw Android notification keys
- `StatusBarNotification`, `Notification.Action`, `RemoteInput`, or
  `PendingIntent` serialization
- Full model prompts or responses containing user data

Use short retention for hackathon telemetry and provide a one-action delete
path. Operator exports must be redacted by default.

## Offline trace tooling

```bash
node tools/run-rehearsal/index.mjs --scenario confirmed-awaiting-device
node tools/run-rehearsal/index.mjs --scenario stale-event --json \
  | node tools/trace-report/index.mjs
```

The renderer is deliberately a presentation tool, not an evidence verifier.
Integration should render live traces from authenticated Core queries and apply
the receipt proof rule at query time.

## Integration checklist

- Lead adds a versioned shared `TraceEvent` only after all agents agree on the
  logical fields above.
- Core indexes trace events by `traceId`, `occurredAt`, and correlation IDs.
- Android persists perception/execution spans locally before sync where loss
  would hide a safety event.
- Bridge writes structured logs to stderr and sends trace records through Core,
  never MCP stdout.
- UI maps evidence class to an immutable mode badge.
- Alert on listener disconnect, reconciliation failure, stale heartbeat, command
  expiry, and receipt timeout without exposing message content.

