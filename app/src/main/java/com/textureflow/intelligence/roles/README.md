# `intelligence/roles/` — council roles (Stream H)

Pure Java. Inject a `ModelPort` (usually `ModelLifecycle`). No `android.*`.

| Type | Role |
|------|------|
| `TriageRole` | One structured call → `triage.v1.txt` → SchemaValidator → PolicyGate |
| `TriageRoleRequest` | Shielded context + deterministic result + event versions |
| `TriageRoleResult` | Classification; `hasDraft()` is always false |

## How Stream G should call `TriageRole`

```text
ModelLifecycle life = ModelPorts.lifecycle(context);   // NoModelPort if file missing
String prompt = PromptAssets.loadTriage(context);
TriageRole triage = new TriageRole(life, prompt);

// After A0 deterministic + EscalationRules:
if (life.isThermalOrBatteryBlocked() || !life.isAvailable()) {
    // A5 — publish deterministic, source=DETERMINISTIC_FALLBACK
} else if (rule is A3 or A4) {
    TriageRoleResult out = triage.classify(new TriageRoleRequest(shielded, det));
    // out.getSource() is ON_DEVICE_MODEL or DETERMINISTIC_FALLBACK
    // out.hasDraft() is always false; injection still classifies
}
life.unloadIfIdle(now);          // 300 s
// from Activity: life.onTrimMemory(level);
```

Lazy load happens on the first `classify` → `generate`. Do not call `load()` on every notification.
