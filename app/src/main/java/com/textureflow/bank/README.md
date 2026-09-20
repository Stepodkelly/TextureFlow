# `bank/` — paired outbound notification bank (planned)

This folder is reserved for the **messenger-agnostic** paired “bank” design:

- keep the latest **replyable** conversation notification for a peer × app
- **snooze** it so the shade stays clean
- **wake** it (~100 ms reschedule) to cold-start a send
- use a local **outbox** for unsent follow-ups
- **refill** with a branded handshake when both phones are online

WhatsApp / Telegram / Instagram / SMS are examples of `packageName` lanes — not hard-coded owners of this module.

## Planned files (not built yet)

```text
bank/
  NotificationBank.java       ← state machine per (peer × package)
  BankStore.java              ← durable keys + snooze deadlines
  BankArming.java             ← snooze / refresh / wake
  OutboundMessageOutbox.java  ← queued texts until REPLY handle is live
  HandshakeRefill.java        ← peer online branded refill
  README.md                   ← this file
```

Until those exist, related behavior still lives in:

- `notifications/` — capture + snooze APIs
- `actions/` — live RemoteInput send
- `connection/` — cloud outbox (different from local messenger outbox)
- `ui/` — user confirm / voice
