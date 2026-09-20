# Start here (beginner map)

TextureFlow is a **phone app + cloud brain + voice bridge**.

Read this file first. Then open only the folder you need.

## The big picture (4 parts)

```text
YOUR PHONE                          THE CLOUD                         YOUR COMPUTER
───────────                         ─────────                         ─────────────
app/          Android phone app     convex/     backend database      texture-bridge/
              reads notifications               & rules               talk to the phone
              shows the eye UI                                        via voice tools
              can reply/snooze

                                    intelligence/   ranking helper     tools/
                                    shared/         shared types       demo & check scripts
```

## What each top-level folder is for

| Folder | Plain English | When you open it |
|--------|---------------|------------------|
| **`app/`** | The Android phone app (what you install) | Changing UI, notifications, replies, haptics |
| **`convex/`** | Cloud backend (devices, events, proposals) | Changing server rules / data |
| **`texture-bridge/`** | Voice/MCP bridge (computer ↔ phone proposals) | Voice tools, demos with a laptop agent |
| **`intelligence/`** | Decides what is urgent (with safe fallback) | Priority / ranking logic |
| **`shared/`** | Shared contracts used by several parts | Types that must match everywhere |
| **`tools/`** | Scripts to test and demo | Running checks, smoke tests |
| **`docs/`** | Longer explanations | Deep reading after this map |

## Suggested reading order

1. This file (`START_HERE.md`)
2. [`app/README.md`](app/README.md) — phone app map
3. [`docs/CODE_MAP.md`](docs/CODE_MAP.md) — “where does feature X live?”
4. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — full system (advanced)
5. Only then: the other docs in [`docs/`](docs/)

## One sentence per major idea

- **Notification** lands on the phone → TextureFlow **listens** and stores a safe copy.
- **Attention** = “what should I look at now?”
- **Proposal** = “I want to reply/snooze/dismiss” (not done yet).
- **Confirm** = human says yes.
- **Receipt** = phone actually did it (or blocked it).

## Do not edit (usually)

- `node_modules/`, `**/build/`, `.gradle/` — generated
- `.env.local`, `.env.convex` — secrets (use `.env.example` as the template)
- `convex/_generated/` — created by Convex tooling
