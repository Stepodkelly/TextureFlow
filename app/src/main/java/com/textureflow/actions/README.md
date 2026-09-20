# `actions/` — do the thing after confirm

| Important file | Role |
|----------------|------|
| `NotificationActionExecutor.java` | Reply / dismiss / snooze execution |
| `LiveActionRegistry.java` | In-memory RemoteInput handles (not durable!) |
| `NotificationControl.java` | Listener dismiss / snooze API (implemented by NLS) |
| `ConfirmedProposal.java` | Proof the user confirmed |
| `ActionReceipt.java` | Result after attempt |
| `ActionType.java` | REPLY, DISMISS, SNOOZE |

Cold-start handles live in `bank/` (arm/wake). Execution still goes through this package after confirm.

