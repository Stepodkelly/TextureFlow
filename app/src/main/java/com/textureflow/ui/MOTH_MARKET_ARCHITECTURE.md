# Moth Market — Architecture

**Status:** Plan for the next product layer. Part I attention (Waves 0–3) is already in the APK. This file is the product architecture for Moth Market itself: how the chat surface, the private Moth, and a typed memory plane work together.

**Owner:** Moth Market UI (`ui/`) plus a new memory package (not yet built).

**Source of intelligence contracts:** [`docs/INTELLIGENCE_PRODUCTION.md`](../../../../../../../docs/INTELLIGENCE_PRODUCTION.md) (Part I built, Part II dormant).
**Related:** this folder’s [`README.md`](README.md), [`docs/THREAT_MODEL.md`](../../../../../../../docs/THREAT_MODEL.md), [`docs/MODEL_BENCHMARK.md`](../../../../../../../docs/MODEL_BENCHMARK.md).

This is not implementation. It is the locked shape so later streams can own files without redesigning the product.

---

## 0. One-page thesis

Moth Market is the only place the user *talks* to TextureFlow. Other apps (WhatsApp, SMS, Telegram, …) remain the place friends talk to the user. TextureFlow sits in the middle: it sees notifications, ranks them, remembers what matters, and — slowly, in the background — can speak *inside the conversation as a private moth* that only the user can see.

The on-device model is slow (Gemma 3 1B int4 on the current Xiaomi is ~2.75 tok/s, tier **T1**). That is fine. The model is not a live chat partner. It is a background reader. The product that makes it useful is **memory**: typed ledgers of schedules, people, wants, and the links between them, so a later proposal is grounded instead of generic.

**Understanding is automatic. Acting is confirmed. Memory is sourced. Sharing never happens by itself.**

The Moth never sends a message. It only proposes. Edit / Send sit side by side under the proposal; × dismisses it. Confirmation still goes through the existing `CommandPolicy` → `NotificationActionExecutor` path. No new execute method on any intelligence interface.

---

## 1. What already exists (do not rebuild)

Waves 0–3 on `build/W0-foundation` already give Moth Market:

| Layer | What |
|---|---|
| Capture | `TextureNotificationListenerService` → `NotificationNormalizer` → SQLite events |
| Rank | `DefaultAttentionEngine`: deterministic first, then optional `TriageRole` |
| Safety | `ContextShield`, `PolicyGate`, injection drop, literal-words drafts |
| Model | MediaPipe + Gemma 3 1B int4, lazy load, 300s idle unload, Wi-Fi download |
| UI | Chat list ranked URGENT > IMPORTANT > recency; glow; reason line; teal pip for on-device refine |
| Draft | `ProposalDraft` → read-back → `ConfirmedProposal` |
| Voice | “What needs me?” speaks deterministic first, then a summary if one arrives |
| Settings | Intelligence toggle, download, “Test this phone”, last-N metrics |
| Metrics ledger | `SqliteLedger`: ticks, assessments, role runs, proposals (no bodies) |

**Deliberate limits that stay:** SQLite not Room. One structured call first (no SkepticRole). Part II helpers/sharding stay contracts-only until a memory job actually needs them. The engine still never sees `PendingIntent` / `RemoteInput`.

The gap: the app ranks *this notification* and can draft *this reply*. It does not yet remember that David asked for 2pm, that Rosy wants a chicken coop, or that John builds coops. That is this plan.

---

## 2. Product stories this plan adds

**US-6 — Private moth in the thread**

In the conversation with Sam, a moth bubble appears that only I can see. It is not sent to Sam. It can tell me a conflict, a useful connection, or a finished background search.

**US-7 — Propose, edit, or send**

When the moth (or the engine) proposes a reply, the card shows the exact text, then **Edit** and **Send** side by side in Moth Market chrome. A small **×** on the top-right dismisses it. Send is still exact-match confirmation. Edit puts the text in the existing editor.

**US-8 — Schedule conflict**

David says “eat at 2pm.” Anna later says “meet at 2:10.” The moth, in Anna’s thread, says there may be a clash with David — as a question, not a fact, because we may not have seen my reply to David.

**US-9 — Queued curiosity**

A thread mentions wanting a pasta place. That goes on a “things you may want to know” queue. Later, idle, a workflow may search (only if web access is opted in) and the moth reports back in that thread. The search query is “pasta, near X” — never names or message text.

**US-10 — Grounded introduction**

Rosy wants help building a chicken coop. Profiles already say John builds coops. The moth, in Rosy’s thread, proposes I mention John — or, if confidence is low, just a friendly note: “John might be good for this.” It never messages John, and it never tells Rosy anything automatically.

**US-11 — Quiet by default**

The moth does not comment on every message. It speaks when a conflict, a direct unanswered ask, a useful connection, or a finished workflow crosses a threshold. There is a daily cap and a quiet switch.

---

## 3. Capture reality (the constraint that shapes memory)

The listener mostly sees **incoming** notifications. What I typed inside WhatsApp is usually invisible unless I sent it *through* TextureFlow.

So:

- A schedule line from David starts as `proposed`, not `confirmed`.
- “You are eating with David at 2” is forbidden copy. Allowed copy: “David suggested 2:00 — Anna now says 2:10. Do those clash?”
- A reply sent through TextureFlow can raise an entry to `confirmed` or `declined`.
- Profiles and facts always carry a **source** (event id, person, time, confidence). They never become anonymous “the app knows.”

This is not a bug to paper over. It is the data model.

---

## 4. Logical architecture (updated)

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ MOTH MARKET (ui/)                                                        │
│  Chat list · thread · moth bubbles (local-only) · proposal card          │
│  Edit | Send + × · voice · glow · settings                               │
└───────────────┬───────────────────────────────┬─────────────────────────┘
                │ AttentionListener             │ MemoryListener (new)
                │ ProposalDraft                 │ MothUtterance, FactView
┌───────────────▼──────────────┐  ┌─────────────▼─────────────────────────┐
│ ATTENTION ENGINE (exists)    │  │ MEMORY PLANE (new)                    │
│  tick FSM A0–A5              │  │  Extractor (rules first, model later) │
│  Triage / Summary / Draft    │  │  Fact store (typed, sourced)          │
│  PolicyGate on model JSON    │  │  Conflict / link rules                │
│  IntelligenceLedger (metrics)│  │  Workflow queue                       │
└───────────────┬──────────────┘  │  Moth voice (when to speak)           │
                │                 └─────────────┬─────────────────────────┘
                │ reads events                  │ reads events + writes facts
┌───────────────▼───────────────────────────────▼─────────────────────────┐
│ DATA                                                                         │
│  NotificationRepository (SQLite) · TextureFlowDatabase v2+                  │
│  Memory tables (new, same helper, not Room)                                  │
│  Metrics ledger (ticks / assessments / proposals) — still no bodies          │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ CAPTURE                                                                      │
│  NLS → Normalizer → upsert → enqueueAttention (honors intelligence_mode)     │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ ACTION PLANE (unchanged)                                                     │
│  ProposalDraft → user Edit/Send/× → ConfirmedProposal → CommandPolicy        │
│  → NotificationActionExecutor → receipt / texture                            │
│  The moth has no send path of its own.                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

**New invariants (add to I1–I10):**

- **I11.** Moth utterances are local UI only. They are never written to the posting app, never synced, never a `REPLY` unless the user hits Send on a *proposal* (a different object).
- **I12.** Every stored fact has `sourceEventId`, `sourcePersonId`, `saidAt`, `status`, `confidence`, `kind`. Unsourced facts are invalid.
- **I13.** Facts from notification bodies start as `unverified`. Injection-flagged events cannot write facts.
- **I14.** A fact about person B attributed to person A is stored as “A said about B”, never as B’s own profile, until the user confirms or B says it.
- **I15.** Workflows that leave the phone (web search, cloud) are opt-in, show the exact outbound payload, and never include names or message text.
- **I16.** Sensitive categories (health, money, sexuality, precise location of a third party) are not inferred and not stored.
- **I17.** The moth speaks only when `MothGate` allows it (threshold + daily cap + quiet + not injection).
- **I18.** Memory jobs never block capture, ranking, or the listener thread. Newest event still wins (same coalescing as attention ticks).

---

## 5. The moth in the conversation

### 5.1 Visual

- The moth is a **participant bubble** in `ConversationController`, visually distinct (moth mark, muted ink, not the friend’s color, not “me”).
- It is omitted from any send / RemoteInput path. `ChatListPresenter` grouping ignores moth rows for “their last message.”
- Accessibility: `contentDescription` starts with “Moth, only you can see this.”
- It can appear while the thread is open *or* be waiting the next time the user opens that person.

### 5.2 What the moth is allowed to say

| Kind | Example | Needs proposal card? |
|---|---|---|
| `NOTE` | “David also mentioned 2pm today.” | No |
| `CONFLICT` | “Anna’s 2:10 may clash with David’s 2:00.” | No (optional “propose a reply”) |
| `QUESTION` | “Did you agree to dinner with David?” | No |
| `INTRO` | “John builds coops — worth mentioning to Rosy?” | Optional |
| `WORKFLOW_DONE` | “A few pasta places near you, if you want them.” | No |
| `DRAFT` | Proposed reply text | **Yes** — Edit / Send / × |

Notes and questions are `MothUtterance`. Drafts are still `ProposalDraft` (existing type). The moth bubble can *contain* a draft card; it does not replace the draft type.

### 5.3 Proposal card (US-7)

Under the proposed text, in Moth Market chrome (`UiKit` / `MothMarketTheme`):

```text
┌─────────────────────────────────────────────┐
│  ×                                          │  ← top-right, dismiss / cancel
│  Reply to Sam on WhatsApp:                  │
│  “I'm coming down.”                         │
│                                             │
│  [ Edit ]              [ Send ]             │  ← equal weight, side by side
└─────────────────────────────────────────────┘
```

- **Send** = existing confirm path (`ProposalFlowPresenter.toConfirmedProposal` → `CommandPolicy`). Exact text, exact event version.
- **Edit** = put text in the existing response editor; title “Revised reply”; Send still requires a later confirm.
- **×** = `invalidate` / user-dismissed. Terminal. Does not send. Does not store the payload.
- Stale / invalidated (event removed or version change) already clears the editor; keep that, and drop the card.

Do not add extra buttons (snooze, dismiss notification) on this card. Those stay on the existing response options.

### 5.4 When the moth stays quiet

`MothGate` (pure Java, table-tested) returns silent unless:

1. Intelligence mode is on.
2. Quiet hours / quiet switch are off.
3. Today’s moth utterances for this person (or globally) are under the cap (default: 6/day global, 2/person).
4. The trigger is one of: schedule conflict, unanswered direct ask older than N minutes, confirmed useful link above confidence, workflow finished, or the user just asked in this thread.
5. The source event is not injection-flagged.

Ranking and glow continue even when the moth is silent.

---

## 6. Memory plane

A second plane next to attention. Attention answers “what needs me *now*.” Memory answers “what is true *enough* to ground the next thing I say.”

### 6.1 One fact store, many kinds

Do not invent a separate SQLite table per vibe. One `memory_facts` table, discriminated by `kind`, plus a `memory_links` table for edges, plus `memory_jobs` for the queue. Same `TextureFlowDatabase` helper, new `migrateTo3`.

**Fact kinds (v1 set — extend by additive enum, never by a new execute path):**

| Kind | What | Example |
|---|---|---|
| `SCHEDULE` | Time-bounded intent involving people | David, eat, 14:00 local, today |
| `PERSON_TRAIT` | Stable-ish thing about a person | John builds coops; Rosy wants a coop |
| `WANT` | Something the *user* may want to know or do | pasta place, near home |
| `COMMITMENT` | User said they would do X (only if we saw the send) | “I'll send the file tonight” |
| `OPEN_LOOP` | Unanswered question aimed at the user | Maya: “dinner still at nine?” |
| `PLACE` | Named place tied to a person or plan | “the park by the station” |
| `PREFERENCE` | User preference stated to someone | vegetarian, evenings better |

**Statuses:** `unverified` → `likely` → `confirmed` | `declined` | `expired` | `retracted`.

**Confidence:** 0–1, engine-set. Model scores cannot raise a fact to `confirmed`. Only (a) the user editing the ledger, or (b) a TextureFlow-sent reply that matches the slot, can confirm.

**Retention:** facts 90 days unless `confirmed` and still in the future (schedule) or pinned. Jobs 14 days after terminal. Purge from the existing health job.

**No bodies in fact rows.** Store extracted slots (`when_start`, `when_end`, `title`, `person_id`, `place_label`) plus `source_event_id`. The body stays on the notification event table and can be joined when building moth copy.

### 6.2 Links

`memory_links`: `from_id`, `to_id`, `rel`, `source_event_id`, `confidence`.

Relations (v1): `KNOWS`, `SUGGESTED_FOR` (John → Rosy’s coop), `CONFLICTS_WITH` (two schedules), `ABOUT` (want → person), `SAME_THREAD`.

The Rosy/John case is a `SUGGESTED_FOR` link with low confidence until the user acts on a moth note. The moth is allowed to *mention* the link in Rosy’s thread. It is not allowed to open a thread to John, and not allowed to put Rosy’s request into John’s profile as a fact John stated.

### 6.3 Extractor: rules first, model second

On this handset (T1), extraction is mostly deterministic:

| Slot | How |
|---|---|
| Times / dates / “at 2”, “tomorrow”, “Sunday” | Patterns + `java.time` in the user’s zone |
| “let’s / meet / dinner / come / eat” | Existing urgency + new schedule patterns |
| Person names | `IdentityResolver` / `PersonKeys` already used by the engine |
| Places | Light patterns (“at the …”, “near …”) — low confidence |
| “I want / looking for / know a good …” | Want patterns → `WANT` + maybe a job |
| Injection | Existing detector; **write nothing** |

The model (A3/A4/idle only) may propose extra slots as JSON through `SchemaValidator` → `PolicyGate`. Same as triage: failure → keep the rule-based facts, ignore the model. The model cannot delete facts; it can only add `unverified` rows.

Idle / charging / battery-not-saver is when we run **profile synthesis** (summarize traits per person from the last N facts) and **link suggestions**. That is also the natural Part II depth job later (JobClass `memory.synthesize`, `allowDepth=true` when helpers exist). Until then it is a local sequential job that can be skipped.

### 6.4 Conflict engine (pure code)

Schedule conflict is not a model judgement.

```text
overlap(A, B) if same calendar day (or explicit range)
  and person-sets are not obviously disjoint?  -- optional
  and at least one of A,B involves the user
  and neither is declined/expired
→ emit CONFLICT moth in the *newer* thread
```

Copy is always hedged if either fact is not `confirmed`.

### 6.5 Workflow queue (“things you may want to know”)

`memory_jobs`:

| Field | Meaning |
|---|---|
| `job_id` | Stable id |
| `class_id` | e.g. `local.lookup.place`, `local.remind.schedule`, `web.search.place` |
| `payload_json` | **Minimized** slots only (`{"topic":"pasta","area":"shibuya"}`) |
| `status` | `queued` / `running` / `done` / `failed` / `blocked_needs_consent` |
| `origin_fact_id` | Why this exists |
| `speak_in_person_id` | Which thread gets the moth note when done |

`web.search.*` stays `blocked_needs_consent` until Settings → Intelligence → “Allow web lookups” is on, and the first run of each class shows the payload. No names. No message text. No phone numbers.

Local jobs (remind, compile a moth note from facts we already have) need no extra consent.

This is the same *idea* as Part II `JobClass`, and should use that type so a later helper can run `web.search.place` or a bigger synthesize job without a new API.

---

## 7. Friend profiles

A profile is a **view** over facts + links for one `person_id`, not a blob the model rewrites.

```text
Person: John
  traits:   "builds coops" (from John's own messages, likely)
  wants:    —
  links:    SUGGESTED_FOR Rosy / chicken coop (unverified, from Rosy's thread)
  open:     —
  next:     —
```

UI (later, not phase 1): a person sheet from the chat row, listing facts the user can edit / forget. One-tap **Forget this person** drops facts, links, jobs, and moth history for that id.

The moth uses the view when drafting or noting. If the view is thin, it says less. Inventing a trait to sound helpful is a PolicyGate fail (`changes_meaning` / new `ungrounded_claim` rule).

---

## 8. How attention and memory talk

Do not merge the two engines. Attention stays fast and per-event. Memory is slower and append-only.

```text
onEvent (already coalesced on the attention thread)
  1. existing tick → assessment → UI
  2. enqueue ExtractJob(eventId) on a memory executor (single thread)
       rules extract → maybe model extract if A3/A4 and not hot
       write facts (unverified)
       run conflict rules → maybe MothUtterance
       maybe enqueue a workflow job
  3. UI: moth bubble if MothGate says so
```

Drafts: `requestDraft` already exists. Phase 1 just changes how the draft is *shown* (card in the thread). Phase 3+ passes a **memory snippet** into the drafter context (shielded, token-budgeted, never raw other-people’s messages — only fact slots: “David SCHEDULE 14:00 unverified”).

Summaries: “what needs me?” can mention a conflict if one is open. Deterministic line still speaks first.

---

## 9. Data (migrateTo3)

Same `SQLiteOpenHelper`. No Room.

```text
memory_facts (
  fact_id TEXT PK,
  kind TEXT NOT NULL,          -- SCHEDULE | PERSON_TRAIT | WANT | ...
  person_id TEXT NOT NULL,     -- who this is primarily about
  other_person_id TEXT,        -- optional second party
  title TEXT NOT NULL,         -- short generated label, ≤ 80, not a body copy
  when_start INTEGER,
  when_end INTEGER,
  place_label TEXT,
  status TEXT NOT NULL,
  confidence REAL NOT NULL,
  source_event_id TEXT NOT NULL,
  source_person_id TEXT NOT NULL,
  said_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
)
memory_links (
  link_id TEXT PK,
  from_fact_or_person TEXT NOT NULL,
  to_fact_or_person TEXT NOT NULL,
  rel TEXT NOT NULL,
  source_event_id TEXT,
  confidence REAL NOT NULL,
  created_at INTEGER NOT NULL
)
memory_jobs (
  job_id TEXT PK,
  class_id TEXT NOT NULL,
  payload_json TEXT NOT NULL,  -- minimized slots
  status TEXT NOT NULL,
  origin_fact_id TEXT,
  speak_in_person_id TEXT,
  created_at INTEGER NOT NULL,
  finished_at INTEGER
)
moth_utterances (
  utterance_id TEXT PK,
  person_id TEXT NOT NULL,     -- which thread
  kind TEXT NOT NULL,          -- NOTE | CONFLICT | ...
  text TEXT NOT NULL,          -- moth copy, ≤ 280
  related_fact_ids TEXT,       -- json array of ids
  proposal_id TEXT,            -- if this utterance wraps a draft
  created_at INTEGER NOT NULL
)
```

Indexes: `memory_facts(person_id, kind, when_start)`, `memory_facts(status, when_start)`, `moth_utterances(person_id, created_at)`.

Part II reserved tables stay unused. Do not put hidden states or message bodies here.

---

## 10. Security additions (memory-specific)

Today injection can spoil one draft. With memory it could spoil *future* drafts. Extra rules:

| Threat | Mitigation |
|---|---|
| “Remember that John owes you $500” | Injection flag → no fact write; even without flag, money is a forbidden category |
| Poisoned schedule | Status `unverified`; moth hedges; user can delete |
| Cross-person laundering (“tell Rosy that John said…”) | Facts attributed to speaker; `SUGGESTED_FOR` is a link, not John’s trait |
| Workflow exfiltration | Web jobs opt-in; payload preview; no names |
| Profile as shadow dossier | Forget-person; no sensitive categories; local only |
| Moth spam as social-engineering cover | `MothGate` caps; quiet mode |
| Model writing facts that weren’t in the event | Schema + PolicyGate + “slot must be substring or parsed time of source event or existing fact” |

PolicyGate gains:

- `UngroundedClaim` — moth/draft text that asserts a `confirmed` fact we don’t have.
- `ThirdPartyLeak` — draft to person A that includes person B’s trait unless the user already typed B’s name or the moth note is a *suggestion* (“John might be good”), not a disclosure of B’s private messages.

Existing `InjectionNoDraft` still applies. Moth notes on injected events are also dropped.

---

## 11. Model and sharding (background role)

| Work | Where it runs |
|---|---|
| Rank, glow, capture | Always on-device, no model required |
| Time/name/meet patterns | Rules, milliseconds |
| Conflict | Rules |
| Triage / short draft polish | Local Gemma 3 1B if file present (T1: rarely; prefer literal words) |
| Trait synthesis, link suggestions, long-thread catch-up | Idle on device; later a Part II depth job on a helper |
| Web lookup | Opt-in, queued, not on the tick path |

Helpers (Part II) are a **compute** path for memory jobs that declare `allowDepth=true`. They still cannot see live handles, cannot send, and their JSON still hits PolicyGate on the phone. The moth remains a primary-phone UI object.

Gemma 4 / LiteRT-LM stays deferred until RAM and Kotlin runtime allow it. The memory plane must work on T0 (rules + silent moth except conflicts we can prove from times).

---

## 12. Settings additions (Stream L already owns this surface)

Keep the current Intelligence block. Add, without redesigning sensory/core:

| Control | Default |
|---|---|
| On-device intelligence | on (exists) |
| Moth in conversations | on |
| Quiet moth | off |
| Daily moth cap | 6 |
| Remember schedules | on |
| Remember people / links | on (can off) |
| Allow web lookups | **off** |
| Forget this person | per-profile |
| Clear all memory | confirmation |

“Test this phone” stays the §9.4 wizard. Metrics view can add last-N facts (kind + title only, no bodies) next to assessments.

---

## 13. Package layout (when we build)

```text
app/.../intelligence/
  engine/          (exists) attention ticks
  memory/          NEW  MemoryPlane, Extractor, ConflictRules, MothGate, FactStore
  memory/jobs/     NEW  WorkflowQueue, job classes
  ledger/          metrics only (do not overload with facts)
  roles/           existing + optional MemoryExtractRole (JSON slots)

app/.../ui/
  chats/           ConversationController shows moth bubbles + new proposal card
  chats/MothBubble.java, ProposalCard.java
  intel/           AttentionUiBinder also binds MemoryListener
  people/          NEW  PersonSheet (phase 3+)
  settings/        moth / memory / web-lookup toggles
```

File ownership for a later BUILD_PLAN wave:

| Stream | Owns |
|---|---|
| N | `intelligence/memory/**` (extract, facts, conflicts, gate) — no Android except store |
| O | `ui/chats` moth bubble + Edit/Send/× card |
| P | `migrateTo3` + `SqliteFactStore` |
| Q | workflow queue + web opt-in (settings) |
| R | person sheet + forget |
| S | evals: schedule conflicts, injection-does-not-write-facts, ungrounded draft drop |

Attention engine files stay Stream G’s unless a one-line enqueue to memory is required — that enqueue lives on `NotificationRuntime` like `enqueueAttention`.

---

## 14. Build phases

| Phase | Ships | Why this order |
|---|---|---|
| **1 — Surface** | Moth bubble in the thread; proposal card Edit / Send / × | Uses drafts that already work; biggest visible change; no new storage |
| **2 — Time** | `SCHEDULE` facts from rules; conflict moth; hedged copy | Clearest value; almost no model |
| **3 — Facts** | Full `memory_facts` + sources; user-visible / editable list; forget | Base layer; injection tests here |
| **4 — Jobs** | Want-queue; local workflows; web search behind opt-in | Background work suits T1 |
| **5 — Graph** | Links, profiles-as-views, Rosy/John-style notes inside proposals | Most powerful, needs 3–4 |

Each phase: `tools/test-android.sh` green, no `android.*` in pure memory packages, PolicyGate tests for the new rules, no push from agents.

**Out of scope until asked:** cloud assist, Gemma 4, SkepticRole, automatic messages to a third person, reading full WhatsApp history (we only have notifications), marketplace of skills.

---

## 15. Copy rules for moth text

- Never claim knowledge we didn’t source.
- Prefer “X said …” / “X suggested …”.
- Prefer questions when status ≠ `confirmed`.
- Never put other people’s private wording into a *proposal* to someone else. A suggestion (“John might be good for this”) is allowed; a quote of John’s message to Rosy is not, unless the user typed it.
- Keep moth notes ≤ 280 characters (same cap as drafts).
- Same untrusted wrap if a note is later fed back into a model.

---

## 16. Open decisions (defaults, so we can build)

| # | Decision | Default in this plan |
|---|---|---|
| 1 | Confirm schedules from WhatsApp-typed replies? | No — only TextureFlow-sent replies or user edit |
| 2 | Web lookups | Off until Settings opt-in |
| 3 | HOUSEHOLD / helpers for synthesize jobs | Dormant (Part II) |
| 4 | Person sheet UI | Phase 3, not phase 1 |
| 5 | Moth in group chats | Phase 2+; v1 moth is 1:1 threads only |
| 6 | Time zone | Device local zone |
| 7 | Language | Same as notification text; no translation job in v1 |
| 8 | Dual-write facts into metrics ledger? | No. Facts ≠ metrics. Separate tables |

---

## 17. Success (what “a lot better” means)

The app feels different when:

1. Opening a thread can show a moth note that is *specific* (a time clash, a forgotten ask), not a generic summary.
2. A proposed reply is one tap to send, one tap to edit, one tap to drop — without leaving the thread.
3. After a week of real notifications, the user can open a person and see a short, editable list of what TextureFlow thinks it heard — and delete any of it.
4. Nothing in that list came from an injection-shaped message.
5. Ranking, capture, and replies still work with the model file deleted (T0).

That is the harness: many ledgers, sourced, connected, quiet, and always behind confirm.
