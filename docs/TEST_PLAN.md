# TextureFlow Test Plan

## Quality contract

The release gate protects five invariants:

1. Notification ingestion is local-first and reconciles after listener loss.
2. Consequential actions preserve proposal -> confirmation -> command -> receipt.
3. Event version, ownership, expiry, capability, and idempotency are checked at
   execution time.
4. Only Android creates authoritative receipts; no receipt means no success claim.
5. Speech and native accessibility semantics remain sufficient when every
   texture channel is disabled.

The current Agent F tooling exercises shared contract fixtures offline. Android,
Convex, Bridge, and Texture Engine owners must add their native unit,
instrumentation, and integration tests to satisfy the full matrix.

## Offline checks

```bash
node tools/validate-contracts/index.mjs
node --test tools/test/*.test.mjs
node tools/run-rehearsal/index.mjs --all
```

The harness validates `NotificationEvent`, `ActionProposal`, and
`TextureCommand` artifacts against contract version 1. It intentionally has no
network client, credential support, Android executor, or receipt factory.

## Test layers

| Layer | Owner | Required proof |
| --- | --- | --- |
| Contract | Lead + all | Type/enum/timestamp fixtures pass in TypeScript and Kotlin |
| Unit | Component owner | State transitions, parsers, policy, formatting, cue arbitration |
| Android instrumentation | Android agents | Real listener, Room, process recovery, `RemoteInput`, haptics, TalkBack |
| Core integration | Core agent | Auth, atomic confirm/queue, claim, expiry, idempotency, receipt ownership |
| MCP integration | Bridge agent | Tool schemas, session isolation, stderr logging, no immediate-send tool |
| Distributed E2E | Integration lead | Real message through authenticated Android receipt and second-phone proof |
| Accessibility | QA + user testers | Voice-only, TalkBack, low vision, hearing/haptic variants |
| Rehearsal | QA | Deterministic offline trace with conspicuous label and no receipt |

## End-to-end scenario matrix

| ID | Scenario | Expected result | Receipt/cue rule |
| --- | --- | --- | --- |
| E01 | One active reply-capable message | Appears once with sender, app, body, version, priority, `REPLY` | No receipt |
| E02 | Read attention by voice | At most three ranked active items, speech-friendly and source-grounded | Read cue only |
| E03 | Prepare reply | Exact recipient, app, and text preview; proposal bound to event version | `CONFIRMATION_REQUIRED`; no command |
| E04 | Revise reply | New proposal content/version invalidates prior confirmation | No command until new confirmation |
| E05 | Confirm once | Atomic grant creates one command with one idempotency key | Success waits for Android receipt |
| E06 | Decline/cancel | Proposal becomes `CANCELLED` | `CANCELLED`; no command |
| E07 | Confirm twice | One command and one device action maximum | Return/reuse original outcome; no second success cue |
| E08 | Notification updates before confirm | Proposal becomes `STALE`; confirmation rejected `EVENT_CHANGED` | `ACTION_FAILED`; no execution |
| E09 | Notification removed before confirm | Confirmation rejected `NOTIFICATION_GONE` | `ACTION_FAILED`; no execution |
| E10 | Reply action disappears/changes | Android rejects `ACTION_HANDLE_CHANGED` or `REPLY_NOT_SUPPORTED` | Failure receipt from Android if command was claimed |
| E11 | Command expires before claim | Android does not execute | Android/Core records `EXPIRED`; failure cue |
| E12 | Device heartbeat stale | Core rejects write before queuing command | No receipt, no success cue |
| E13 | Network drops after local ingestion | Room retains event and durable outbox later syncs it once | No lost/duplicate event |
| E14 | Network drops after dispatch | Android stores receipt locally and retries upload; never re-executes | One device receipt identity |
| E15 | Malicious notification instructions | Content remains data; it cannot call tools, confirm, change recipient, or alter policy | No unauthorized command |
| E16 | Ambiguous person | Voice asks clarification; session reference remains unbound | No proposal |
| E17 | Unauthorized owner/device | Core and Android reject | `UNAUTHORIZED`; no action |
| E18 | PendingIntent cancelled | Android records `PENDING_INTENT_CANCELLED` | Explicit failure, never `DISPATCHED` |
| E19 | Voice session restarts | Pronouns and active proposal do not cross session boundary | New explicit proposal required |
| E20 | Rehearsal confirmed path | Contract-valid queued command stops before device execution | Banner says `REHEARSAL`; no receipt |
| E21 | TalkBack speaks during movement | Carpet/focus audio ducks or stops; speech remains clear | Haptic/visual redundancy preserved |
| E22 | Sound or haptics disabled | Workflow remains fully operable by remaining channels | No missing essential meaning |
| E23 | Second messaging app | Capability is detected from its current notification, not package assumption | Same policy/receipt path |
| E24 | Source app reports only dispatch | Voice says `dispatched`, not `delivered` | Wording matches evidence |

## Notification reader hardening

Notification callbacks are edges, not durable truth. The source of truth is the
combination of Android's current active-notification snapshot, the live action
registry, and the local Room ledger. Ingestion must never wait for Convex.

Required implementation behavior:

- `onNotificationPosted` copies safe fields, hashes normalized content, versions
  updates, stores one local transaction, refreshes the live action handle, and
  enqueues sync.
- `onNotificationRemoved` marks the event removed, invalidates proposals, clears
  the action handle, and enqueues sync.
- `onListenerConnected` immediately calls `getActiveNotifications()`, rebuilds
  the private action registry, and reconciles Room before declaring healthy.
- `onListenerDisconnected` records unhealthy state and requests platform rebind
  with bounded backoff; repeated callbacks must not create a hot loop.
- App launch, package replacement, permission regrant, and boot recovery verify
  listener enablement and wait for a successful active-snapshot reconciliation.
- A persistent last-callback time, last-reconciliation time, active count,
  outbox depth, and listener state are visible to the local status surface and
  trace system without exposing notification bodies.
- Event IDs and content versions remain stable across process restarts.
- Sync retries are idempotent and cannot block callback threads.
- Commands execute only against the current in-memory action handle and current
  event version; persisted capability metadata alone is insufficient.

### Notification survival matrix

| ID | Fault injection | Acceptance criteria |
| --- | --- | --- |
| N01 | Normal post | Local event committed once before network sync |
| N02 | Same notification posted twice unchanged | No new version and no duplicate attention item |
| N03 | Same key with changed body/action | Version increments once; prior proposal stales |
| N04 | Removal callback | Local status `REMOVED`; handle gone; proposal invalid |
| N05 | Listener process killed with active notifications | On reconnect, active snapshot repopulates events and action handles without duplicates |
| N06 | Notification arrives while process is absent and remains active | Reconciliation discovers it within one connection cycle |
| N07 | Listener disconnect callback | Health turns unhealthy immediately; bounded rebind is requested |
| N08 | Notification access revoked | UI/trace states permission loss; writes are blocked; no reconnect spin |
| N09 | Access regranted | Fresh active-snapshot reconciliation completes before healthy state |
| N10 | App package replaced | Service reconnects and reconciles; event IDs remain deterministic |
| N11 | Device reboot | Permission state is detected; active notifications reconcile after service connection |
| N12 | Doze/locked for 15 minutes | Incoming visible notification is captured or recovered by reconciliation |
| N13 | 100 posts/updates in a burst | Callback remains lightweight; Room/outbox converge without ANR or lost final versions |
| N14 | Convex unavailable for 10 minutes | Local events persist; ordered/idempotent outbox drains after recovery |
| N15 | App background restricted | Health reports degraded; no false connected status |
| N16 | Notification removed during command claim | Version/handle recheck prevents dispatch |
| N17 | Action handle replaced with same visible text | Handle fingerprint/version change prevents old proposal execution |
| N18 | Phone time changes | Ordering/idempotency use stable IDs and server/device timestamps explicitly; expiry is conservative |
| N19 | Force-stop then manual relaunch | App explains force-stop state and reconciles after launch; no claim of continuous coverage |
| N20 | Reconnect loop 20 times | No duplicate listeners, excessive battery use, duplicate events, or duplicate sync jobs |

Important platform limit: if a notification is posted and removed entirely while
the listener process/service is unavailable, Android's later active snapshot
cannot reconstruct it. TextureFlow must not promise impossible historical
coverage. It can detect and disclose the observation gap using listener-health
timestamps. Force-stop is also user-enforced suspension, not an automatically
recoverable process death.

Notification reliability release gate:

- N01-N18 and N20 pass on the physical demo phone.
- Three process-death recoveries converge to the same active event set.
- A 10-minute network outage loses no locally observed event or Android receipt.
- Listener health never says connected before reconciliation completes.
- The judged build records a recent successful reconciliation during preflight.

## Policy and state-transition assertions

- `PROPOSED` or `REVISED` may become `CONFIRMED`, but a command appears only in
  the same atomic transaction that commits valid confirmation.
- `CANCELLED`, `EXPIRED`, and `STALE` proposals cannot become committed.
- A command's owner, proposal ID, event ID/version, action type, payload, target
  device, expiry, and idempotency key are immutable.
- Only one device can atomically claim a command.
- A duplicate command ID or idempotency key returns the prior result and never
  calls `PendingIntent.send()` again.
- Android revalidates ownership, expiry, current event, version, capability, and
  action handle immediately before execution.
- Bridge never narrates success from command status alone.

## Accessibility test checklist

Perform on the physical demo phone at minimum. Passing automated checks does not
replace testing with blind and low-vision users.

### Voice-only

- Complete read, prepare, revise, confirm, cancel, and failure flows without
  touching or seeing the phone.
- `stop`, `cancel`, `repeat`, and `go back` interrupt safely.
- Ambiguity prompts name choices without relying on screen order.
- Spoken previews include recipient, source app, exact action, and exact content.
- Speech never says delivered when only dispatch is proven.

### TalkBack and focus

- Every interactive element exposes native role, accessible name, state, and
  consequence.
- Focus order matches listening -> attention -> proposal -> confirm/cancel ->
  receipt.
- No decorative textile or Eye-of-Horus element steals focus.
- Rapid focus movement coalesces bumps and does not obscure TalkBack.
- TalkBack announcement cancels or ducks nonessential audio immediately.
- Explore-by-touch and keyboard/switch traversal reach the same controls.

### Low vision and layout

- Test system font at 100%, 150%, and 200% without clipping or hidden commands.
- Test display size increased, portrait/landscape, and smallest supported phone.
- Text contrast remains sufficient over moth texture and glass surfaces.
- High Contrast/Reduced Texture removes visual noise without removing hierarchy.
- Color is never the only indicator for urgency, selection, error, or success.
- Touch targets remain at least Android's recommended size with visible focus.

### Sound, haptics, and motion

- Audio textures never carry essential meaning alone.
- Haptic patterns never carry essential meaning alone.
- Carpet sound plays only during motion and stops at rest, speech, backgrounding,
  or deadline.
- Success and failure patterns are clearly distinct at reduced intensity.
- Off, Reduced, Standard, Strong, Low Stimulation, and Visual Only profiles
  produce the documented channels.
- No cue startles at system volume; test speaker, wired/Bluetooth audio, and mute.
- Basic-vibrator devices receive a meaningful fallback.
- Reduced-motion mode stops ambient drift and repeated animation.

### Cognitive and fatigue checks

- A first-time user can learn the five P0 cues from previews, not memorization.
- Ten repeated navigation actions do not become irritating or physically tiring.
- Failure text and speech identify what happened and the next safe action.
- Sensory settings are reversible and never require a textured cue to find.

## Performance budgets

Initial hackathon targets, measured with trace events:

| Path | Target |
| --- | --- |
| Notification callback -> local commit | p95 under 250 ms |
| Listener connected -> active snapshot reconciled | p95 under 2 s for 50 active items |
| Local commit -> Core sync on healthy network | p95 under 2 s |
| Voice tool call -> proposal response | p95 under 1.5 s without model drafting |
| Confirmation -> Android command claim | p95 under 1 s |
| Claim -> device receipt persisted | p95 under 1.5 s excluding source app delay |
| Cue scheduled -> render start | p95 under 100 ms |

Report sample count and device/network conditions; do not present single-run
timings as a percentile.

## Release scorecard

A build is demo-ready only when:

- Offline contracts and Agent F tests pass.
- Android notification reliability gate passes on the physical demo phone.
- One real E01-E05 path passes three times consecutively.
- E06-E20 safety/failure paths have current proof.
- Accessibility checklist has no P0 blocker.
- Live trace contains one authenticated Android receipt matching the command.
- No logs contain raw message bodies, credentials, Android notification keys, or
  `PendingIntent` representations.

