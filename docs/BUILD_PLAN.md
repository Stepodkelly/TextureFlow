# TextureFlow — Build Plan for Sub-Agents

> Status: **plan only, nothing built yet.**
> Source of truth for *what* to build: [`INTELLIGENCE_PRODUCTION.md`](INTELLIGENCE_PRODUCTION.md) (Part I + Part II §33–§34 contracts).
> This file says *who builds what, in what order, touching which files*.

---

## 0. Scope

**In scope (this plan):**

| Doc phase | What | Why now |
|---|---|---|
| P0 | Engineering hygiene: CI, test setup, `MainActivity` split | Makes parallel work safe |
| P1 | Deterministic engine in Java (port of `priority.ts`, shield, PolicyGate, ledger) | App gets real ranking with no model |
| P2 | Eval set + harness | Every later step is measured |
| P3 | One on-device model port + **single structured Triage call** | First real intelligence |
| P4 | Summary + draft (Skeptic only if evals show it helps) | User-facing value |
| P6 | Part II *contracts only* (`JobClass`, reserved tables, capability fields) | Cheap now, avoids rework |

**Out of scope:** P5 cloud assist, P7–P9 helpers/sharding. They stay documented in the intelligence doc.

**Deliberate deviations from the intelligence doc** (agents follow these; doc gets synced in Wave 3):

1. Storage stays on the existing `SQLiteOpenHelper` (`TextureFlowDatabase`), **not Room**. The doc says Room; the codebase doesn't use it and adding it isn't worth the churn.
2. Model work starts with **one** structured call (Triage). Council roles are split out only if the eval harness shows a gain.
3. All new logic is **pure Java with no Android imports** where possible, so it runs in the existing fast JUnit suite (like `connection/` and `policy/` today).

---

## 1. How agents work

### 1.1 Isolation

- Each workstream runs in its **own git worktree/branch** (`best-of-n-runner` sub-agent type), named `build/<stream-id>-<slug>`, e.g. `build/A-triage-port`.
- The coordinator (main agent) merges branches in the order in §6, runs the full check, and resolves conflicts.
- Agents **do not push** and **do not merge**. They finish with a clean branch and a report.

### 1.2 File ownership (the main rule)

An agent may only edit files it owns (§4, §5). If it needs a change elsewhere, it writes it in its report under **"Requests for other streams"** instead of editing.

Shared hot spots and their single owner:

| File / area | Owner | Everyone else |
|---|---|---|
| `app/build.gradle`, `settings.gradle`, `gradle/` | Wave 0, then **Stream E** (Wave 1), then **Stream H** (Wave 2) | Request changes |
| `data/TextureFlowDatabase.java` | **Stream C** | Request tables |
| `ui/MainActivity.java` + new `ui/**` files | **Stream F** (Wave 1), then **Stream I** (Wave 2) | Don't touch |
| `notifications/NotificationNormalizer.java` | **Stream G** (Wave 2) | Don't touch |
| `intelligence/api/**` (frozen types) | **Wave 0** only; changes after freeze go through the coordinator | Read only |
| `shared/evals/**` | **Stream D** | Read only |
| `docs/INTELLIGENCE_PRODUCTION.md` | Coordinator (Wave 3) | Report deviations |

### 1.3 Done means

Every task is done only when:

- `JAVA_HOME=<Android Studio JBR> ./gradlew testDebugUnitTest` passes (script added in Wave 0: `tools/test-android.sh`).
- `npm run check` passes.
- New code has unit tests; new pure-Java classes have **no** `android.*` imports unless listed as allowed.
- Small commits, one logical change each, imperative messages (e.g. "Port priority features to Java").
- Package `README.md` updated if a package gains files (matches existing repo convention).
- Report written (template in §8).

### 1.4 Code conventions (match existing code)

- Java 17, final classes, static factory or constructor injection, no DI framework.
- Clock and randomness injected (see `ConnectionClock`) so tests are deterministic.
- No new third-party dependencies except where a stream explicitly owns that decision (E, H).
- Comments only for constraints the code can't show.

---

## 2. Target package layout

```text
app/src/main/java/com/textureflow/intelligence/
  api/          AttentionEngine, AttentionListener, EventSignal, AttentionAssessment,
                AttentionLevel, AssessmentSource, SummaryQuery/Result, DraftQuery,
                ProposalDraft, CapabilityProfile, Callback, JobClass, DataClass     (Wave 0)
  triage/       DeterministicTriage, PriorityFeatures, PriorityPatterns,
                IdentityResolver, PersonAliasBook                                    (Stream A)
  shield/       ContextShield, ShieldedContext, TokenBudget, TokenEstimator          (Stream B)
  policy/       PolicyGate, PolicyVerdict, ProposalBinding, SchemaValidator          (Stream B)
  ledger/       IntelligenceLedger (interface), InMemoryLedger, SqliteLedger         (Stream C)
  model/        ModelPort, ModelRequest/Response, NoModelPort, <chosen>ModelPort,
                ModelLifecycle, CapabilityProbe                                      (E → H)
  roles/        TriageRole, SummarizerRole, DrafterRole, SkepticRole, prompts        (H, K)
  engine/       DefaultAttentionEngine, EscalationRules, ConfidenceMerger,
                TickScheduler                                                        (Stream G)
  jobs/         JobClassRegistry (P6 stub, local-only)                               (Stream G)
app/src/main/assets/intelligence/prompts/  triage.v1.txt, summary.v1.txt, draft.v1.txt
app/src/test/java/com/textureflow/intelligence/**   mirrors the above
shared/evals/                          cases + golden outputs                        (Stream D)
tools/evals/                           TS golden exporter, report script             (A, D)
```

---

## 3. Waves at a glance

```text
Wave 0  (1 agent, blocking, ~0.5–1 day)
  W0 Foundation: CI, test script, frozen api/ types, DB migration hook

Wave 1  (6 agents in parallel, ~2–4 days)
  A Triage port ─┐
  B Shield+Gate ─┤
  C Ledger/DB   ─┼──► Wave 2
  D Evals       ─┤
  E Model bench ─┤
  F UI split    ─┘

Wave 2  (4 agents, ~3–5 days)
  G Engine + wiring   (needs A, B, C)
  H Model port + Triage role   (needs E, B, D)
  I UI integration    (needs F; G's API)
  J E2E emulator test (needs F; finishes after G)

Wave 3  (2–3 agents)
  K Summary + Draft roles (+ Skeptic if evals justify)
  L Settings, benchmark wizard, metrics view
  M Doc sync + cleanup (coordinator)
```

---

## 4. Wave 0 — Foundation (one agent, must finish first)

**Branch:** `build/W0-foundation`
**Owns:** `.github/`, `tools/test-android.sh`, `app/build.gradle` (test deps only), `intelligence/api/**`, `data/TextureFlowDatabase.java` (migration hook only).

| # | Task | Details | Acceptance |
|---|---|---|---|
| W0.1 | CI workflow | `.github/workflows/ci.yml`: Node 20 → `npm ci` + `npm run check`; JDK 17 → `./gradlew testDebugUnitTest`; cache Gradle + npm | Green on current `main` |
| W0.2 | Local test script | `tools/test-android.sh` sets `JAVA_HOME` to Android Studio JBR if unset, runs unit tests; mention in `START_HERE.md` | Runs from a clean shell |
| W0.3 | Frozen API types | Create `intelligence/api/` per §2 with **data classes + interfaces only**, no logic. Mirror names from `intelligence/src/types.ts` and `shared/contracts/domain.ts` (`AttentionLevel` = LOW/NORMAL/IMPORTANT/URGENT; `AssessmentSource` = DETERMINISTIC, DETERMINISTIC_FALLBACK, ON_DEVICE_MODEL). Include `JobClass` + `DataClass` from doc §33 (P6). `AttentionEngine`/`AttentionListener` exactly as doc §7 — no execute/send methods | Compiles; a test asserts no `api` type references `PendingIntent`, `RemoteInput`, or notification keys (reflection check on field types/names) |
| W0.4 | Migration hook | Bump structure so `onUpgrade` runs ordered steps (`migrateTo2(db)` etc.), still at version 1. No new tables (Stream C adds them) | Existing tests pass; fresh install unchanged |
| W0.5 | Test deps | Add only if needed for pure-Java tests (keep JUnit 4 + org.json). No Robolectric yet | — |

**Freeze:** after W0 merges, `intelligence/api/**` changes need coordinator approval and a note to all active streams.

---

## 5. Wave 1 — Parallel streams

### Stream A — Deterministic triage port

**Branch:** `build/A-triage-port` · **Owns:** `intelligence/triage/**`, its tests, `tools/evals/export-golden.mjs`
**Reads:** `intelligence/src/priority.ts`, `aliases.ts`, `fallback.ts`, `shared/evals/intelligence-cases.json`

| # | Task | Details |
|---|---|---|
| A1 | Port patterns | `PriorityPatterns`: the five regexes from `priority.ts`, case-insensitive, same word boundaries. Unit-test each with positive/negative strings |
| A2 | Port features + score | `DeterministicTriage.assess(event, identity, now)` → `PriorityResult` with identical weights (0.3/0.25/0.2/0.15/0.1), promo ×0.25, malicious cap 0.49, `roundScore`, `levelForScore`, `priorityReason` strings verbatim |
| A3 | Port identity resolution | `IdentityResolver` from `aliases.ts` (RESOLVED / AMBIGUOUS / PROVISIONAL) |
| A4 | Port `mergeModelPriority` | Bounded ±0.1 merge, only in the 0.35–0.75 band |
| A5 | Golden parity | `tools/evals/export-golden.mjs` runs the TS functions over a fixed input set (evals + ~50 generated edge cases: empty body, emoji, mixed case, very old timestamps, each package family) → `shared/evals/golden/triage.json`. Java test loads it and asserts **exact** score/level/reason equality |
| A6 | Speed test | 1,000 assessments < 200 ms on JVM (guards the < 50 ms per-event target) |

**Done:** golden parity 100 %, no Android imports. **Request to D:** own the golden file location going forward.

### Stream B — Context shield and PolicyGate

**Branch:** `build/B-shield-gate` · **Owns:** `intelligence/shield/**`, `intelligence/policy/**`, tests
**Reads:** `intelligence/src/context.ts`, `schemas.ts`, `fallback.ts`; doc §6.4, §8; `docs/THREAT_MODEL.md`

| # | Task | Details |
|---|---|---|
| B1 | `ContextShield` | Build doc §8.1 shape from events: wrap bodies as untrusted, ≤ 5 events, drop keys/phone numbers/absolute timestamps, relative `ageMinutes`, strip other people's messages |
| B2 | `TokenBudget` + `TokenEstimator` | Pluggable estimator (default: chars/4 heuristic; model port may supply a real tokenizer later). Trim oldest-first by tokens, never mid-codepoint. Report `inputTokens`, `droppedTokens`, `contextOverflow` |
| B3 | `SchemaValidator` | Strict JSON parse for Triage/Summary/Draft/Skeptic outputs (doc §6.1), length caps, enum checks. Reject unknown fields |
| B4 | `PolicyGate` | Every row of doc §6.4 as a separate, named rule with its own test. Output `PolicyVerdict { accepted, capped, stale, dropped, reasons[] }` |
| B5 | Proposal binding | `ProposalBinding` = (eventId, eventVersion, packageName, recipient, actionType, payloadHash); SHA-256 payload hash; tests for every field change invalidating |
| B6 | Injection test pack | Port every injection string from TS tests + threat model T01/T03/T13 into a shared test resource; gate must drop model drafts on flagged events |

**Done:** each §6.4 rule has a failing-input test; no Android imports.

### Stream C — Ledger and database

**Branch:** `build/C-ledger-db` · **Owns:** `data/TextureFlowDatabase.java`, `intelligence/ledger/**`, `data/README.md`, tests

| # | Task | Details |
|---|---|---|
| C1 | Schema v2 | `migrateTo2`: `attention_ticks`, `assessments`, `role_runs`, `proposals`, `capability_profile` (doc §11.1) **plus reserved P6 tables** `nodes`, `compute_sessions`, `depth_jobs`, `boundary_reports`, `tick_outcomes` (doc §34), empty and unused. Bump `DATABASE_VERSION` to 2 |
| C2 | `IntelligenceLedger` interface | `recordTick`, `recordAssessment`, `recordRoleRun`, `upsertProposal`, `transitionProposal`, `loadCapabilityProfile`/`save`, `purgeOlderThan` |
| C3 | `InMemoryLedger` | Full fake for other streams' unit tests |
| C4 | `SqliteLedger` | Real implementation on `TextureFlowDatabase`; proposal payload text deleted on terminal status |
| C5 | Retention | 14-day purge, triggered from existing health job or app start (report which; don't edit the job file — request it) |
| C6 | Tests | Contract test suite run against `InMemoryLedger` in JUnit; same suite against `SqliteLedger` as an **instrumentation test** (`androidTest/`), plus a v1→v2 migration test |

**Done:** no bodies stored in tick/assessment/role tables (test asserts columns).

### Stream D — Eval set and harness

**Branch:** `build/D-evals` · **Owns:** `shared/evals/**`, `tools/evals/**` (except A's exporter), `app/src/test/.../intelligence/evals/**`

| # | Task | Details |
|---|---|---|
| D1 | Case format v2 | Extend `intelligence-cases.json` schema: `id, app, sender, relationship, body, ageMinutes, expectedLevel, expectedRequiresResponse, tags[] (injection, promo, urgent, ambiguous, multilingual, emoji, long)`. Keep the 5 existing cases |
| D2 | Grow to ~120 cases | ~40 normal chat, ~20 urgent, ~20 promos, ~15 injection, ~15 ambiguous, ~10 long/group. Synthetic but realistic. Add `shared/evals/README.md` section: how the user adds anonymised real notifications |
| D3 | Java eval runner | JUnit test that runs any `Triage` implementation over the cases and writes `build/reports/evals/triage.json` + a markdown summary: level accuracy, URGENT precision/recall, injection pass rate, promo suppression |
| D4 | Thresholds | Gate: injection pass 100 % (hard fail); others reported, not failing, until baseline is agreed |
| D5 | Baseline | Record the deterministic baseline numbers in `shared/evals/BASELINE.md` (after A merges; can use TS version before that) |

### Stream E — Model runtime benchmark spike

**Branch:** `build/E-model-bench` · **Owns:** `intelligence/model/**` (interface + bench only), `app/build.gradle` (Wave 1 only), `app/src/androidTest/.../model/**`, `docs/MODEL_BENCHMARK.md`

| # | Task | Details |
|---|---|---|
| E1 | `ModelPort` interface | `load()`, `generate(ModelRequest) → ModelResponse {text, inputTokens, outputTokens, wallMs}`, `unload()`, `tokenCount(String)`; plus `NoModelPort` |
| E2 | Candidate spikes | Behind the interface, try **B: LiteRT-LM / MediaPipe LLM Inference + Gemma small** and **C: llama.cpp via JNI + GGUF (1–2B, Q4)**. Note A (Gemini Nano / AICore) availability but don't depend on it (not on emulator) |
| E3 | Benchmark test | Instrumentation test: 600-token prefill + 64-token decode, one Triage-shaped prompt; record load time, RAM, tok/s, APK size delta, model file size |
| E4 | Report | `docs/MODEL_BENCHMARK.md`: table per runtime × device (emulator + user's real phone), recommendation, integration cost |
| E5 | Keep it removable | Spike code behind a Gradle flag or in a debug source set so Wave 2 can delete the loser cleanly |

**Needs from the user:** a real Android phone over USB for E3 (emulator numbers are not representative).
**Decision point:** user picks the runtime from E4 → unblocks Stream H.

### Stream F — `MainActivity` decomposition (behaviour-preserving)

**Branch:** `build/F-ui-split` · **Owns:** `ui/**`
**Rule:** no behaviour or visual change. Before/after emulator screenshots of chats, conversation, flows, settings must match.

| # | Task | Extract from `MainActivity` (current methods) |
|---|---|---|
| F1 | `ui/kit/UiKit` | `surface`, `text`, `value`, `supportingValue`, `sectionHeading`, `pageColumn`, `scrollPage`, dp helpers |
| F2 | `ui/chats/ChatListController` | `buildChatsPage`, `renderPeople`, `filterChatList`, `personRow`, glow-ring registration |
| F3 | `ui/chats/ConversationController` | `buildChatThreadPage`, `openConversation`, `messageBubble`, `closeConversation`, response options + proposal render/confirm/cancel flow |
| F4 | `ui/nav/NavigationController` | `buildNavigation`, `navTab`, `selectNavTab`, `showPage` |
| F5 | `ui/lighting/ReflectionLightsController` | `updateReflectionLights` + ring slot logic |
| F6 | `ui/settings/SettingsPage`, `ui/flows/FlowsPage` | `buildSettingsPage`, sensory prefs restore/persist, `buildFlowsPage` |
| F7 | `ui/voice/VoiceSessionController` | `initializeVoice`, `startVoiceTurn`, `handleVoiceUtterance`, `speakUrgentItem` glue |
| F8 | Presenter seams | Where logic is pure (filtering, person grouping, proposal state), move to plain classes with unit tests |

**Target:** `MainActivity` < 400 lines (lifecycle, wiring, permissions).
**Hand-off to I:** a `ChatListController.setAssessments(Map<personId, AttentionAssessment>)` seam, no-op for now.

---

## 6. Wave 2 — Integration streams

Merge order before Wave 2 starts: **W0 → C → A → B → D → F → E**.

### Stream G — Attention engine + capture wiring

**Branch:** `build/G-engine` · **Owns:** `intelligence/engine/**`, `intelligence/jobs/**`, `notifications/NotificationNormalizer.java`, `policy/AttentionQueuePolicy.java`
**Needs:** A, B, C merged

| # | Task | Details |
|---|---|---|
| G1 | `DefaultAttentionEngine` | Single-thread executor; per-person coalescing (newest wins); tick sequence doc §5.2 steps 1–4, 6–9 (step 5 via `ModelPort`, `NoModelPort` by default) |
| G2 | `EscalationRules` A0–A5 | Pure function `(features, trigger, capability) → Rule`; table-driven tests for every row |
| G3 | `ConfidenceMerger` | `systemConfidence`; only it can reach URGENT; model-URGENT without signals capped to IMPORTANT |
| G4 | Replace crude priority | `NotificationNormalizer.priority(body)` → `DeterministicTriage`; DB columns unchanged. Parity test: listener path produces same level as engine |
| G5 | Listener → engine | After repository write, enqueue `EventSignal`; never block the listener (test with a slow fake port) |
| G6 | Invalidation | `EVENT_REMOVED` / version change → `onProposalInvalidated` |
| G7 | `JobClassRegistry` (P6) | Registers built-in local job classes (triage, summary, draft) with `allowDepth=false`; engine rejects unknown classes |
| G8 | Queue policy | `AttentionQueuePolicy` consumes `AttentionAssessment` instead of raw strings (keep old overload until I merges) |

### Stream H — Model port + Triage role

**Branch:** `build/H-model-triage` · **Owns:** `intelligence/model/**`, `intelligence/roles/**`, `assets/intelligence/prompts/**`, `app/build.gradle` (Wave 2)
**Needs:** E decision, B merged, D harness

| # | Task | Details |
|---|---|---|
| H1 | Production port | Chosen runtime behind `ModelPort`; delete losing spike |
| H2 | `ModelLifecycle` | Lazy load on first A3/A4 tick, unload after 300 s idle and on `onTrimMemory`; thermal/battery-saver checks → A5 |
| H3 | `CapabilityProbe` | Builds `CapabilityProfile` (doc §9.1 + empty Part II fields from §30); tier T0–T3 |
| H4 | Model download | On-demand, Wi‑Fi only, size shown, SHA-256 verified, resumable; stored in app-private storage |
| H5 | `TriageRole` | Single structured call → `triage.v1.txt`; output via `SchemaValidator` → `PolicyGate` |
| H6 | Eval gate | Run D's harness with model on: must not reduce injection pass rate; report accuracy delta vs deterministic baseline |

### Stream I — UI integration

**Branch:** `build/I-ui-intel` · **Owns:** `ui/**` (after F merges)
**Needs:** F merged, G's API (can start against `InMemoryLedger` + fake engine)

| # | Task | Details |
|---|---|---|
| I1 | Assessment display | Chat list order + level + reason line from `AttentionListener`; ring glow intensity keyed to level |
| I2 | Source indicator | Subtle marker when a result was model-refined vs deterministic |
| I3 | Proposal flow | `ProposalDraft` → read-back → confirm → existing `ConfirmedProposal`/`CommandPolicy`; stale/invalidated handling |
| I4 | Voice | "What needs me?" speaks deterministic top items first, refines when model result arrives |
| I5 | Presenter tests | Unit tests for ordering, stale proposals, invalidation |

### Stream J — End-to-end emulator tests

**Branch:** `build/J-e2e` · **Owns:** `app/src/androidTest/**` (except E/C folders), `tools/e2e/**`

| # | Task | Details |
|---|---|---|
| J1 | Harness | Instrumentation setup; grant notification-listener access via `adb shell cmd notification allow_listener` in a script |
| J2 | Fake notifier | Small test APK or `androidTest` helper posting MessagingStyle notifications with reply actions under a different package |
| J3 | Scenarios | Urgent message ranks first; promo sinks; injection message never produces a model draft; reply confirm sends via `RemoteInput`; removed notification invalidates proposal |
| J4 | CI (optional) | Emulator job on CI if runtime allows; otherwise `tools/e2e/run.sh` for local |

---

## 7. Wave 3

| Stream | Owns | Work |
|---|---|---|
| **K** Summary + Draft | `intelligence/roles/**` | `SummarizerRole`, `DrafterRole` (literal-words-first, doc §6.3); run evals; add `SkepticRole` **only** if a draft eval set shows it catches real issues |
| **L** Settings + metrics | `ui/settings/**`, `intelligence/engine/metrics` | Intelligence mode toggle, "Test this phone" benchmark wizard (doc §9.4), model download UI, simple on-device metrics view from ledger |
| **M** Doc sync | `docs/**` | Coordinator updates `INTELLIGENCE_PRODUCTION.md` (SQLite not Room, single-call-first, benchmark results, chosen runtime), `CODE_MAP.md`, package READMEs |

---

## 8. Agent brief template

Use this verbatim as the sub-agent prompt, filling the brackets:

```text
You are Stream [ID] — [NAME] for TextureFlow (Android, Java 17).
Repo: /Users/stephenkelly/Projects/TextureFlow. Work on branch build/[ID]-[slug] in your own worktree.

Read first: docs/BUILD_PLAN.md (§1 rules, your stream section), docs/INTELLIGENCE_PRODUCTION.md [sections],
[source files to read].

You own ONLY: [paths]. Do not edit anything else. If you need a change elsewhere, list it under
"Requests for other streams" in your report.

Tasks: [paste task table].

Done when: tools/test-android.sh and npm run check pass; every task has tests; no android.* imports in
pure packages; small commits; package README updated.

Do not push or merge. Finish with a report:
1. What was built (files, classes)
2. Tests added and results
3. Deviations from the plan/doc and why
4. Requests for other streams
5. Open questions for the user
```

---

## 9. Coordinator checkpoints

| Checkpoint | After | Demo on emulator |
|---|---|---|
| **C0** | W0 | CI green on GitHub |
| **C1** | Wave 1 merged | App looks identical (F), tests up from 48, eval baseline report exists |
| **C2** | G + I merged | Chat list ranked by the Java engine; reasons shown; injection message flagged |
| **C3** | H merged | Ambiguous message refined by the model on a real phone; evals show no safety regression |
| **C4** | K + L merged | "What needs me?" summary and a confirmed dictated reply end-to-end |

At each checkpoint: run all tests + evals, install on emulator, screenshot, short report to the user.

---

## 10. What the user needs to provide

| When | Input |
|---|---|
| Before Wave 1 | OK to proceed with the deviations in §0 |
| During Wave 1 (E) | A real Android phone on USB for benchmarks |
| During Wave 1 (D) | Optional: 20–100 real notifications, anonymised (names/numbers replaced), for evals |
| End of Wave 1 | Pick the model runtime from `docs/MODEL_BENCHMARK.md` |
| Before K | Confirm draft tone/length preferences |

---

## 11. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Merge conflicts in `MainActivity` | Only F then I own `ui/**`; no other stream touches it |
| TS/Java priority drift | Golden parity test (A5) runs in CI |
| Model runtime too slow on real phones | Deterministic path is complete on its own (P1); tier T0/T1 behaviour defined |
| Emulator can't represent model performance | E3 requires a real device; emulator used for correctness only |
| API types need changes mid-wave | Freeze + coordinator approval; broadcast to active streams |
| DB migration breaks existing installs | C6 v1→v2 migration instrumentation test |
| Agents over-build Part II | Only P6 contracts allowed; any helper/network code is rejected at review |

---

*Version 0.1 — derived from INTELLIGENCE_PRODUCTION.md v0.2 and a repo survey (102 Android source files, 48 Android unit tests passing, TS checks passing, no CI).*
