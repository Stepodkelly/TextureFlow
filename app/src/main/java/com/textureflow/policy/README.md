# `policy/` — safety rules

Answers: “Are we allowed to do this right now?”

| Important file | Role |
|----------------|------|
| `CommandPolicy.java` | Gate reply/snooze/dismiss commands |
| `NotificationIngestionPolicy.java` | What notifications we keep |
| `AttentionQueuePolicy.java` | Queue rules; `shouldSurface(AttentionAssessment, replyCapable)` plus the old String overload |
| `CommandFreshness.java` | Expire stale commands |
| `PolicyDecision.java` | Allow / deny result |
