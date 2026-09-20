# Cloud backend (`convex/`)

This is the **server** side of TextureFlow (Convex).

Plain English: the phone syncs notification events here, and the cloud helps
decide attention, store proposals, and hand commands back to the phone.

## Start with these files

| File | What it is |
|------|------------|
| `schema.ts` | The database tables |
| `devices.ts` | Phone registration / online status |
| `events.ts` | Notification events from phones |
| `attention.ts` | What to surface next |
| `proposals.ts` | “I want to do X” before confirm |
| `commands.ts` | Work waiting for the phone |
| `receipts.ts` | Proof of what the phone did |
| `lib/` | Shared helpers (auth, validation, tracing) |
| `_generated/` | Auto-generated — do not edit by hand |

## Beginner tip

Change **schema + one feature file** together. Read `docs/ARCHITECTURE.md` before
big changes.
