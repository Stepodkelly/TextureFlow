# Shared contracts (`shared/`)

Types and fixtures used by more than one part of the project
(phone bridge, intelligence, tools).

## Folders

| Path | What it is |
|------|------------|
| `contracts/` | Shared domain types (actions, events, …) |
| `evals/` | Evaluation cases for intelligence |
| `fixtures/` | Demo event samples |

Keep these small and stable — many packages import them.
