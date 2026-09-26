# `intelligence/roles/` — council roles (Stream H + K)

Pure Java. Inject a `ModelPort` (usually `ModelLifecycle`). No `android.*`.

| Type | Role |
|------|------|
| `TriageRole` | One structured call → `triage.v1.txt` → SchemaValidator → PolicyGate |
| `TriageRoleRequest` | Shielded context + deterministic result + event versions |
| `TriageRoleResult` | Classification; `hasDraft()` is always false |
| `SummarizerRole` | `summary.v1.txt` → `SUMMARY_V1` → PolicyGate; fallback concatenates newest shielded bodies ≤ 240 chars |
| `DrafterRole` | Literal-words-first (`draft.v1.txt` → `DRAFT_V1` or `DRAFTER`); injection never calls the model |
| `SkepticRole` | **Not added.** Draft evals do not yet justify it. PolicyGate already drops injection drafts. |

## How the engine should call these

```text
if (!(model instanceof NoModelPort) && tier != T0) {
    SummarizerRoleResult out = new SummarizerRole(model).summarize(
            new SummarizerRoleRequest(shielded, det));
    // out.getSource() is ON_DEVICE_MODEL or DETERMINISTIC_FALLBACK
    // record RoleRunRecord on the existing tick id

    DrafterRoleResult draft = new DrafterRole(model).draft(
            new DrafterRoleRequest(shielded, userRequest, userDraftText, tone));
    // complete user words are returned verbatim; generate() is not called
    // injection → user's literal words or empty; no model draft
}
// any exception → deterministic fallback (summary concat / user's words)
```

`requestSummary` / `requestDraft` stay on `TickScheduler` (non-blocking).
Draft ticks do not yet exist — skip ledger role-run writes rather than inventing a tick.

Prompts ship in `assets/intelligence/prompts/`. `PromptAssets` still only loads
`triage.v1.txt`; Stream L should add `summary.v1.txt` / `draft.v1.txt` loaders.
The roles also expose `DEFAULT_PROMPT` so the engine stays Android-free.

Lazy load happens on the first `generate`. Do not call `load()` on every request.
