# TextureFlow Demo Script

This runbook separates the live product proof from the offline rehearsal. A live
demo may claim an action was **dispatched** only after TextureFlow Core receives
an authenticated `ActionReceipt` created by the Android device. The rehearsal
harness never creates receipts and must remain visibly labeled `REHEARSAL`.

## Roles and equipment

- Presenter: speaks to VoiceOS and narrates the product idea.
- Operator: watches health and trace state without touching the demo flow.
- Sender: sends the test message and verifies the reply on the second phone.
- Android demo phone with TextureFlow and one reply-capable messaging app.
- Second phone with a synthetic contact/account and visible message thread.
- MacBook with VoiceOS, TextureFlow Bridge, and the trace presentation.
- Phone stand, charging cables, and a quiet audio route tested in the room.

Use synthetic accounts and non-sensitive messages. Do not show terminals,
configuration files, tokens, API keys, phone numbers, or personal notification
history to judges or recordings.

## Offline preflight

Run these from the repository root. They require Node 22, use no network, and
read no credentials.

```bash
node tools/validate-contracts/index.mjs
node --test tools/test/*.test.mjs
node tools/run-rehearsal/index.mjs --all
```

Pass criteria:

- Contract version 1 and both shared notification fixtures validate.
- Every test passes.
- Every scenario says `REHEARSAL - NO LIVE ACTIONS OR RECEIPTS`.
- No rehearsal output contains `receiptId` or claims `ACTION_DISPATCHED`.

## Live setup

Complete this at least 45 minutes before judging and again after moving rooms.

1. Charge both phones above 50%, disable battery saver, and connect both to the
   same known-good network or tested independent networks.
2. Open the receiving conversation on the second phone and send one ordinary
   message to confirm both accounts work.
3. On the Android demo phone, confirm TextureFlow notification-listener access
   is enabled and the app reports the listener as connected.
4. Confirm the app has reconciled active notifications after connection. The
   most recent reconciliation time must be visible and recent.
5. Confirm the target messaging notification exposes `REPLY`; a notification
   without a live `RemoteInput` action is not demo-capable.
6. Confirm TextureFlow Core reports the Android device online with a fresh
   heartbeat, correct owner, and expected app version.
7. Start TextureFlow Bridge and call its read-only status tool. Keep logs on
   stderr; MCP stdout must contain protocol traffic only.
8. Confirm VoiceOS can invoke `texture_status` and
   `texture_list_attention` before sending the judged message.
9. Confirm the trace surface is in `LIVE` mode and empty of previous proposals,
   commands, and receipts. Use only integration-owned reset controls; never
   delete production data with an ad hoc command.
10. Set the sensory profile to Balanced, verify speech ducks texture audio, and
    test the success and failure haptics without issuing an action.
11. Lock the Android phone, place it face down, send a fresh test notification,
    and verify it appears in Core exactly once.
12. Perform one complete real reply and verify the second phone receives it.
    Preserve its trace as the final preflight proof.

Do not force-stop TextureFlow before the demo. Android intentionally prevents a
force-stopped application from restarting normally until the user launches it.
Process-death recovery should be tested separately using platform test tools.

## Live presentation

### Opening, 15 seconds

Presenter:

> TextureFlow turns notifications into one voice-controlled attention stream.
> Voice tells you what something means; texture tells you where you are and what
> just happened.

Keep the Android phone face down and untouched.

### Act 1: voice removes app navigation, 75 seconds

1. The sender messages: `I'm downstairs. The door is locked.`
2. Wait until the live trace shows `EVENT_SYNCED`. Do not verbally announce a
   message before it is visible in the live attention stream.
3. Presenter: `TextureFlow, what needs my attention?`
4. Expected response: `Sam is downstairs and cannot enter.`
5. Presenter: `Tell Sam I'm coming downstairs now.`
6. Expected exact preview: `Reply to Sam on WhatsApp: I'm coming downstairs
   now. Should I send it?`
7. Presenter: `Make that: I'm coming to the lobby now.`
8. Expected revised preview includes the recipient, application, and exact new
   text. The old confirmation must be invalid.
9. Presenter: `Yes, send it.`
10. TextureFlow queues one command. Android claims it, validates the current
    notification version and action handle, and dispatches the live action.
11. The second phone visibly receives the reply.
12. Only after an Android receipt is stored, VoiceOS says: `The reply was
    dispatched through WhatsApp.` The phone emits the glass success cue and firm
    haptic.

Use **dispatched**, not **delivered**, unless the source application supplies
separate delivery evidence.

Expected live trace:

```text
EVENT_RECEIVED -> EVENT_STORED_LOCAL -> EVENT_SYNCED
-> VOICE_TOOL_CALLED -> PROPOSAL_CREATED -> PROPOSAL_REVISED
-> PROPOSAL_CONFIRMED -> COMMAND_QUEUED -> COMMAND_CLAIMED
-> ACTION_DISPATCHED -> RECEIPT_SYNCED -> CUE_RENDERED
```

### Act 2: texture adds orientation, 45 seconds

1. Pick up the Android phone with TalkBack enabled.
2. Move through two attention controls. Each focus boundary gives one glass bump;
   rapid movement must not cause a vibration or audio flood.
3. Scroll the attention surface. Carpet audio runs only while content moves and
   stops immediately at rest or when TalkBack speaks.
4. Focus a consequential control. TalkBack announces its purpose and consequence;
   texture reinforces it but carries no essential meaning alone.
5. Trigger a safe cancellation or preview-only action to demonstrate the soft
   cloth release without sending anything.
6. Toggle Low Stimulation. Essential speech and focus semantics remain usable
   with ambient texture suppressed.

Close with:

> The phone remains the execution authority. VoiceOS can propose; the user
> confirms; Android executes and returns the evidence.

## Recovery playbook

| Symptom | Operator action | Presenter-safe explanation |
| --- | --- | --- |
| Message not observed | Check listener access and connected/reconciled time; relaunch TextureFlow if it was force-stopped; send a new message | "The phone has not observed this notification yet." |
| Duplicate attention item | Stop the demo path; verify event ID/version deduplication; use a new message only after the trace is clean | Do not hide duplicate state |
| Device heartbeat stale | Do not confirm; restore network/app connection and wait for a fresh heartbeat | "The phone is offline, so TextureFlow will not queue the action." |
| Notification changed | Let confirmation fail with `EVENT_CHANGED`; read the update and prepare a new proposal | "The message changed, so the old approval is invalid." |
| Notification removed | Send a new test message and prepare a new proposal | "The original action is no longer available." |
| Reply capability absent | Use the preverified messaging app and a fresh notification | "This notification does not expose a safe reply action." |
| Bridge unavailable | Restart only the Bridge, call status, then resume with a new voice session | "The voice connection is restarting." |
| Model timeout | Use deterministic summary and user-authored reply text | No AI claim is needed for safe execution |
| Convex unavailable | Preserve phone outbox; do not queue a live write; switch to visibly labeled rehearsal | "Here is the interaction model in offline rehearsal." |
| Receipt timeout | Say `The phone has not confirmed dispatch`; never play success or claim send | Preserve trace for diagnosis |
| Audio unavailable | Continue with speech, haptic, and visible state | Mention redundant sensory channels |
| Haptics unavailable | Continue with speech, audio, and visible state | Do not imply vibration occurred |
| TalkBack masks cues | Keep speech primary; nonessential audio should remain suppressed | This is expected arbitration |

After any recovery, create a new proposal. Never reuse an expired, stale,
cancelled, or previously committed proposal.

## Labeled rehearsal fallback

The rehearsal demonstrates contracts, policy, and trace shape only. It does not
contact Convex, VoiceOS, Android, or a messaging application.

```bash
node tools/inject-demo-event/index.mjs --fixture evt_demo_sam
node tools/run-rehearsal/index.mjs --scenario proposal-only
node tools/run-rehearsal/index.mjs --scenario confirmed-awaiting-device
node tools/run-rehearsal/index.mjs --scenario stale-event --json \
  | node tools/trace-report/index.mjs
```

Before switching, say plainly:

> Live transport is unavailable, so this is a labeled rehearsal. It will stop
> before Android execution and will not produce a receipt.

Never overlay rehearsal events on a `LIVE` trace surface. Never import a JSON
file and present it as authenticated device evidence.

## Freeze checklist

- Three consecutive live runs pass from a fresh incoming notification.
- At least one run is performed locked and face down.
- Listener process-death and reconnect reconciliation have passed that day.
- Duplicate confirmation causes one dispatch at most.
- Stale and removed notifications cannot execute.
- Success speech and cue wait for the device receipt.
- TalkBack, sound-off, haptics-off, and Low Stimulation paths remain usable.
- The final build, device configuration, accounts, network, and demo wording are
  frozen 45 minutes before judging.

