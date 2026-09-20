# `connection/` — phone sync (Convex STUBBED by default)

Default path is **local-only**. Live Convex is optional when `CONVEX_URL` is set.

## Start here

| File | Role |
|------|------|
| `ConnectionMode.java` | STUB vs LIVE |
| `ConnectionEngineFactory.java` | Builds the engine for the current mode |
| `TextureFlowConnectionService.java` | Foreground service + listener health loop |
| `TextureFlowConnectionController.java` | Start / stop from UI |

## Navigate by group

**Stub / local (what you use day-to-day)**

| File | Role |
|------|------|
| `StubRemoteGateway.java` | No-op remote |
| `LocalAckOutboxDelivery.java` | Acks outbox without uploading |
| `LocalCoreActionClient.java` | On-device proposal / confirm / execute |

**Engine / state**

| File | Role |
|------|------|
| `ConnectionEngine.java` | Heartbeat / poll loop |
| `ConnectionStateMachine.java` | ONLINE / BACKING_OFF / … |
| `ConnectionConfig.java`, `ConnectionConfigStore.java` | Endpoint + credentials |
| `ConnectionStatusStore.java` | UI-visible status snapshot |
| `BackoffPolicy.java` (if present) / watchdog helpers | Retry timing |

**Live Convex (optional)**

| File | Role |
|------|------|
| `ConvexHttpGateway.java` | HTTP to Convex |
| `ConvexOutboxDelivery.java` | Upload outbox rows |
| `CoreActionClient.java` | Remote proposal / confirm API |

**Outbox + commands**

| File | Role |
|------|------|
| `AndroidDurableOutbox.java`, `OutboxCoordinator.java` | Queue until ack |
| `CommandProcessor.java`, `CommandEnvelopeValidator.java` | Inbound commands |
| `AndroidConfirmedActionExecutor.java` | Run confirmed actions on device |

## Related

- Cloud event outbox in `data/OutboxStore.java` ≠ messenger bank outbox in `bank/`
- Listener durability also lives in `notifications/` (do not duplicate policy here)

## Tests

`app/src/test/java/com/textureflow/connection/`
