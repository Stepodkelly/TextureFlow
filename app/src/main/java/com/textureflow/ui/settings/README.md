# `ui/settings/` — Settings (Stream L)

Moth Market settings page. Sensory, core, and notification-reader blocks stay as Stream F/I left them. This package adds the Intelligence section below “Latest action”.

| File | Role |
|------|------|
| `SettingsPage.java` | Existing connection / sensory / receipt panels; hosts `IntelligenceSection` |
| `IntelligenceSection.java` | Mode toggle, Gemma download, “Test this phone”, ledger metrics, teal-pip legend |
| `IntelligencePreferences.java` | `texture_surface_preferences` / `intelligence_mode` (default on) |
| `SettingsMetrics.java` | `runtime.ledger()` → `SqliteLedger.getAssessments()` (instanceof + inspectors) |

## Preference keys

| Key | Store | Default | Meaning |
|-----|-------|---------|---------|
| `intelligence_mode` | `texture_surface_preferences` | `true` | On → `MainActivity.setAttentionEngine(runtime.attentionEngine())`. Off → `setAttentionEngine(null)` |

Existing sensory keys (`profile`, `audio`, `haptics`, `shake`, `reduced_texture`, `albedo_strength`) are unchanged.

## Download

Started with `ModelDownloader.gemma3(context).download(DownloadListener)` on `actionExecutor`. Size (~529 MB) is shown before start. Wi-Fi is required (`AndroidNetworkPolicy`); the downloader also enforces it and verifies SHA-256. The `.task` file is never packaged.
