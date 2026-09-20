# `connection/` — phone ↔ Convex cloud

| Important file | Role |
|----------------|------|
| `TextureFlowConnectionService.java` | Foreground connection service |
| `ConnectionEngine.java` | Heartbeat, poll, sync loop |
| `ConvexHttpGateway.java` | HTTP calls to Convex |
| `AndroidDurableOutbox.java` | Queue events until cloud accepts them |
| `CommandProcessor.java` | Handle commands from cloud |
| `CoreActionClient.java` | Create/confirm proposals from the phone |
