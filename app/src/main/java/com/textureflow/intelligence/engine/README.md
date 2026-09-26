# `intelligence/engine/` — attention tick FSM (Stream G)

Runs one attention tick per notification event or user query. Pure Java;
no `android.*` imports.

| File | Role |
|------|------|
| `DefaultAttentionEngine` | `AttentionEngine` implementation: single-thread executor, per-person coalescing, §5.2 steps 1–4 and 6–9 |
| `EscalationRules` | A0–A5 pure function of features, trigger, capability |
| `ConfidenceMerger` | `systemConfidence`; only it can raise URGENT |
| `TickScheduler` | Newest `EventSignal` per person wins |
| `EventStore` / `StoredEvent` | Repository port so tests do not need Android |
| `InMemoryEventStore` | JVM fake |
| `PersonKeys` | Stable person ids from sender name + package |

Deterministic assessment is published before any `ModelPort.generate` call.
Step 5 (council) uses the injected port; default is `NoModelPort` → A5.
