# Model runtime benchmark (Stream E)

Measured **2026-09-26** on the connected phone. Emulator numbers were not collected; Google’s MediaPipe LLM path is not reliable on emulators.

## Device

| Field | Value |
|---|---|
| Phone | Xiaomi M2101K6G (Redmi Note 10 Pro) |
| SoC | SM7150 (Snapdragon 732G) |
| Android | 13 / API 33 / arm64-v8a |
| RAM | 5569 MB total, ~1924 MB free at bench start |
| Free data | ~14 GB |
| Wi‑Fi | on |

## Runtimes

| Runtime | What we did | Result |
|---|---|---|
| **A. Gemini Nano / AICore** | Package probe | **Absent** (`com.google.android.aicore` not installed). Expected on Xiaomi. |
| **B. MediaPipe `tasks-genai:0.10.27` + Gemma 3 1B int4** | Load + 600-token prefill + generate | **Worked.** See table below. |
| **C. llama.cpp JNI + GGUF** | Not built | Deferred: NDK/JNI cost is high; MediaPipe already runs on this phone. |

## MediaPipe + Gemma 3 1B int4

Model: `gemma3-1b-it-int4.task` (554,661,243 bytes, SHA-256 `e3d981c0…bd9dee`). Not in the APK. Pushed to `/data/local/tmp/llm/`.

| Metric | Value |
|---|---|
| Load | **7159 ms** |
| RAM available after load | 1685 MB (≈ 239 MB drop) |
| Prompt size (heuristic) | 600 tokens |
| Generate wall | **10533 ms** |
| Output tokens (heuristic) | 29 |
| Decode speed | **≈ 2.75 tok/s** |
| Output | Valid triage-shaped JSON (`intent=REQUEST`, `urgency=IMPORTANT`) |
| Debug APK | 53 MB (includes MediaPipe native `libllm_inference_engine_jni.so`) |

Tier from the intelligence doc: **T1** (< 5 tok/s). Triage on ambiguous ticks only. Summaries stay deterministic. Drafts should prefer the user’s words.

## Recommendation

**Use MediaPipe + Gemma 3 1B int4 as the first on-device port (Stream H).**

Reasons:

- It loaded and produced schema-shaped JSON on this mid-range Xiaomi in ~10 s.
- AICore is not an option here.
- llama.cpp would only pay off if we later need arbitrary GGUFs or helper-phone sharding.

Constraints to keep:

- Do **not** run the model on every notification. Deterministic ranking stays first.
- Download the 529 MB `.task` on Wi‑Fi, verify the hash, store in app-private files. Do not ship it in the APK.
- Keep MediaPipe as `debugImplementation` until H lands; release stays model-free until then.
- Expect T1 behaviour on Snapdragon 7-series / 6 GB phones. Re-bench a Pixel-class device before promising T2.

## Integration cost (H)

| Item | Estimate |
|---|---|
| Gradle + MediaPipe AAR | Already in debug |
| Model download + hash + Wi‑Fi gate | 1–2 days |
| `ModelLifecycle` (lazy load, 300 s idle unload, thermal) | 1 day |
| Wire `TriageRole` through `SchemaValidator` + `PolicyGate` | 1–2 days |
| llama.cpp JNI | Skip unless a later job class needs a GGUF MediaPipe cannot run |

## How to re-run

```sh
adb install -r -t -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -t -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb push models/gemma3-1b-it-int4.task /data/local/tmp/llm/gemma3-1b-it-int4.task
adb shell am instrument -w \
  -e class com.textureflow.intelligence.model.ModelBenchmarkTest \
  com.textureflow.test/androidx.test.runner.AndroidJUnitRunner
adb pull /storage/emulated/0/Android/data/com.textureflow/files/model-bench.md
```

Xiaomi may show **Install via USB** each time. Approve the dialog on the phone.
