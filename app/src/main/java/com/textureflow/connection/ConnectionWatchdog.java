package com.textureflow.connection;

public final class ConnectionWatchdog {
    public enum Action { NONE, WAKE, RESTART }

    public record Decision(Action action, String reason) {}

    private final long staleAfterMs;
    private final long recoveryCooldownMs;
    private long lastRecoveryAt = Long.MIN_VALUE;

    public ConnectionWatchdog(long staleAfterMs, long recoveryCooldownMs) {
        if (staleAfterMs < 1 || recoveryCooldownMs < 0) {
            throw new IllegalArgumentException("Watchdog timing is invalid");
        }
        this.staleAfterMs = staleAfterMs;
        this.recoveryCooldownMs = recoveryCooldownMs;
    }

    public synchronized Decision inspect(
            long now,
            long lastPollAttemptAt,
            boolean loopScheduled,
            boolean operationInFlight) {
        long age = lastPollAttemptAt <= 0 ? Long.MAX_VALUE : Math.max(0, now - lastPollAttemptAt);
        if (age <= staleAfterMs) return new Decision(Action.NONE, "Poll loop is healthy");
        if (lastRecoveryAt != Long.MIN_VALUE && now - lastRecoveryAt < recoveryCooldownMs) {
            return new Decision(Action.NONE, "Recovery cooldown is active");
        }
        lastRecoveryAt = now;
        if (operationInFlight) {
            return new Decision(Action.RESTART, "Network operation exceeded the poll watchdog deadline");
        }
        if (!loopScheduled) {
            return new Decision(Action.WAKE, "Poll loop was not scheduled");
        }
        return new Decision(Action.RESTART, "Poll loop stopped making progress");
    }
}
