package com.textureflow.connection;

public final class ConnectionStateMachine {
    public enum State {
        STOPPED,
        STARTING,
        REGISTERING,
        ONLINE,
        DEGRADED,
        BACKING_OFF,
        STOPPING
    }

    public record Snapshot(
            State state,
            int consecutiveFailures,
            int watchdogRecoveries,
            long changedAtMillis,
            String detail) {}

    private State state = State.STOPPED;
    private int consecutiveFailures;
    private int watchdogRecoveries;
    private long changedAtMillis;
    private String detail = "Stopped";

    public synchronized Snapshot start(long now) {
        require(state == State.STOPPED, "Connection is already running");
        consecutiveFailures = 0;
        return transition(State.STARTING, now, "Starting connection sidecar");
    }

    public synchronized Snapshot registering(long now) {
        require(state != State.STOPPED && state != State.STOPPING,
                "Cannot register while stopped");
        return transition(State.REGISTERING, now, "Registering device");
    }

    public synchronized Snapshot online(long now) {
        require(state != State.STOPPED && state != State.STOPPING,
                "Cannot become online while stopped");
        consecutiveFailures = 0;
        return transition(State.ONLINE, now, "Device connection is healthy");
    }

    public synchronized Snapshot failure(long now, String failure) {
        require(state != State.STOPPED && state != State.STOPPING,
                "Cannot record a failure while stopped");
        consecutiveFailures++;
        return transition(State.BACKING_OFF, now, safeDetail(failure));
    }

    public synchronized Snapshot degraded(long now, String reason) {
        require(state != State.STOPPED && state != State.STOPPING,
                "Cannot degrade while stopped");
        return transition(State.DEGRADED, now, safeDetail(reason));
    }

    public synchronized Snapshot watchdogRecovery(long now, String reason) {
        require(state != State.STOPPED && state != State.STOPPING,
                "Cannot recover while stopped");
        watchdogRecoveries++;
        consecutiveFailures++;
        return transition(State.DEGRADED, now, safeDetail(reason));
    }

    public synchronized Snapshot beginStop(long now) {
        if (state == State.STOPPED) return snapshot();
        return transition(State.STOPPING, now, "Stopping connection sidecar");
    }

    public synchronized Snapshot stopped(long now) {
        consecutiveFailures = 0;
        return transition(State.STOPPED, now, "Stopped");
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(state, consecutiveFailures, watchdogRecoveries, changedAtMillis, detail);
    }

    private Snapshot transition(State next, long now, String nextDetail) {
        state = next;
        changedAtMillis = now;
        detail = nextDetail;
        return snapshot();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String safeDetail(String value) {
        if (value == null || value.trim().isEmpty()) return "Connection operation failed";
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}
