# `bank/` — paired outbound notification bank

Messenger-agnostic local backend for cold-start sends:

- keep the latest **replyable** conversation notification for a peer × app
- **snooze** it so the shade stays clean (`BankArming`, 55 min rolling)
- **wake** it (~100 ms re-snooze) to cold-start a send
- queue outbound texts in a local **outbox** until a REPLY handle is live
- stub **handshake refill** when the bank slot is empty (prefs only; no network)

WhatsApp / Telegram / Instagram / SMS are `packageName` lanes — not hard-coded owners.

## Read in this order

1. `BankKey` / `BankEntry` / `BankState` — identity + slot shape  
2. `NotificationBank` — facade you call from the listener / UI  
3. `BankArming` — snooze / wake timings  
4. `OutboundMessageOutbox` / `HandshakeRefill` — queue + stub refill  

## Files

```text
bank/
  NotificationBank.java        ← start here (facade)
  BankKey.java                 peer × package identity
  BankEntry.java               durable slot (+ state, snoozeUntil)
  BankState.java               EMPTY → ARMED_SNOOZED → WAKING → LIVE → …
  BankStore.java               SharedPreferences JSON
  BankArming.java              arm / refresh / wake helpers
  OutboundMessage.java         queued outbound text
  OutboundMessageOutbox.java   durable local queue (cap 200)
  HandshakeRefill.java         stub refill requests (no network)
  README.md                    this file
```

## Tests

`app/src/test/java/com/textureflow/bank/`

## Listener wiring

`TextureNotificationListenerService.handlePosted` calls `bank().adoptAndArm(...)`
inside an isolated try/catch so bank/snooze failures never poison listener health.

## Related modules

- `notifications/` — capture + `NotificationControl` snooze APIs
- `actions/` — live RemoteInput send
- `connection/` — cloud outbox (different from this local messenger outbox)
