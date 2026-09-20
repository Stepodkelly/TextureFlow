# Intelligence (`intelligence/`)

Decides **how urgent** a notification feels.

It can use a model when configured, but always has a **deterministic fallback**
so demos still work offline.

## Start here

| Path | What it is |
|------|------------|
| `src/index.ts` | Public entry |
| `src/priority.ts` | Scoring / ranking |
| `src/fallback.ts` | Safe non-AI path |
| `src/schemas.ts` | Strict shapes for model output |
| `tests/` | Unit tests |

## Beginner tip

Intelligence **suggests** priority. It must **not** send messages by itself.
