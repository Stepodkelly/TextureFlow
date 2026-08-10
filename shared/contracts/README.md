# TextureFlow contracts

`domain.ts` is the canonical hackathon contract. Android mirrors these names in
Java/Kotlin domain types. Convex and TextureFlow Bridge import them directly.

Rules:

1. Contract changes are additive unless the contract version is incremented.
2. A proposal always binds to an event ID and exact event version.
3. A command always binds to one confirmed proposal and idempotency key.
4. Only Android creates authoritative execution receipts.
5. Texture cues describe validated state; they never authorize state changes.

