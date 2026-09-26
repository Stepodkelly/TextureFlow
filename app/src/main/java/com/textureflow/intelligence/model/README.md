# `intelligence/model/` — model port (Stream E)

| Type | Role |
|------|------|
| `ModelPort` | load / generate / unload / tokenCount |
| `NoModelPort` | Always present; generate throws |
| `MediaPipeModelPort` | Debug-only MediaPipe LLM Inference (Gemma 3 1B int4) |

llama.cpp JNI is not in this spike. Gemini Nano / AICore is probed at bench time
and is not expected on Xiaomi.

Enable the MediaPipe dependency with `-PenableModelBench=true`.
