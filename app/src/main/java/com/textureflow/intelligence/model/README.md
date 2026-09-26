# `intelligence/model/` — model port (Stream H)

Chosen runtime: **MediaPipe `tasks-genai` + Gemma 3 1B int4**. Not Gemma 4, not llama.cpp, not AICore.

| Type | Role |
|------|------|
| `ModelPort` | load / generate / unload / tokenCount |
| `NoModelPort` | Default when the `.task` is missing; generate throws |
| `MediaPipeModelPort` | Debug-only production port (Gemma 3 1B int4) |
| `ModelLifecycle` | Lazy load on first A3/A4 generate; unload after 300 s idle or `onTrimMemory()` |
| `CapabilityProbe` | `CapabilityProfile` + T0–T3; empty Part II shard lists |
| `ModelDownloader` | Wi‑Fi only, resumable, SHA-256, `filesDir/models/` |
| `ModelPorts` | `open(context)` → MediaPipe if file+debug port exist, else `NoModelPort` |
| `PromptAssets` | `loadTriage` / `loadSummary` / `loadDraft` from `assets/intelligence/prompts/` |

## Download

- Artifact: `gemma3-1b-it-int4.task` (554,661,243 bytes)
- SHA-256: `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`
- URL (ungated mirror): `https://huggingface.co/nikhil2024/gemma3-1b-it-litert-mirror/resolve/main/gemma3-1b-it-int4.task`
- Store: `context.getFilesDir()/models/gemma3-1b-it-int4.task` (app-private)
- Resume: `*.task.part` + HTTP `Range`
- **Not in the APK.** Release stays free of the 529 MB file. MediaPipe AAR is `debugImplementation` only.

Dev fallback (Stream E bench path):

```sh
adb push models/gemma3-1b-it-int4.task /data/local/tmp/llm/gemma3-1b-it-int4.task
```

`ModelFiles.resolve` prefers `filesDir/models/`, then that adb path.

`AndroidNetworkPolicy` uses `ACCESS_NETWORK_STATE` (declared in the main manifest).
