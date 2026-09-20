package com.textureflow.notifications;

import com.textureflow.data.ListenerHealthStore;

/** Freshness and status rules for notification-listener durability. */
public final class ListenerHealthPolicy {
    public static final long STALE_AFTER_MS = 20_000L;
    public static final long FORCE_RESTART_AFTER_MS = 45_000L;
    /**
     * Callback silence before a force-rebind. Must be much larger than {@link #STALE_AFTER_MS}:
     * quiet phones legitimately receive no posts/removes; a 45s force loop rate-limits OEMs
     * into permanent listener death.
     */
    public static final long CALLBACK_SILENCE_FORCE_MS = 180_000L;

    private ListenerHealthPolicy() {}

    /**
     * True when the listener reports connected, a recent reconciliation exists, and callbacks
     * look alive. Reconcile alone must not keep health "fresh" while posted/removed callbacks
     * are dead (zombie sticky-connected lockout).
     *
     * <p>Rule: connected && lastReconciledAt within {@link #STALE_AFTER_MS} &&
     * (lastCallbackAt within {@link #FORCE_RESTART_AFTER_MS} OR lastCallbackAt==0 only for the
     * first {@link #STALE_AFTER_MS} after lastConnectedAt).
     */
    public static boolean isFresh(ListenerHealthStore.Snapshot snapshot, long now) {
        if (snapshot == null || !snapshot.connected || snapshot.lastReconciledAt <= 0L) {
            return false;
        }
        long reconcileAge = now - snapshot.lastReconciledAt;
        if (reconcileAge < 0L || reconcileAge > STALE_AFTER_MS) {
            return false;
        }
        return callbacksWithinGrace(snapshot, now, FORCE_RESTART_AFTER_MS, STALE_AFTER_MS);
    }

    /**
     * True when an external watchdog should tear down and rebind the listener:
     * no live in-process connection, sticky connected cleared, reconciliation older than
     * {@link #FORCE_RESTART_AFTER_MS}, or sticky-connected zombie (reconcile still succeeding
     * without callbacks for {@link #CALLBACK_SILENCE_FORCE_MS}).
     */
    public static boolean needsForceRestart(
            ListenerHealthStore.Snapshot snapshot, long now, boolean liveServiceConnected) {
        return probeLooksLockedOut(snapshot, now, liveServiceConnected);
    }

    /**
     * Lockout probe for sticky-connected / zombie-reconcile cases where
     * {@code getActiveNotifications} may still succeed but the reader is not actually live.
     */
    public static boolean probeLooksLockedOut(
            ListenerHealthStore.Snapshot snapshot, long now, boolean liveServiceConnected) {
        if (!liveServiceConnected) return true;
        // markStale clears connected while an in-process flag may still be true until force runs.
        if (snapshot == null || !snapshot.connected || snapshot.lastReconciledAt <= 0L) return true;
        long reconcileAge = now - snapshot.lastReconciledAt;
        if (reconcileAge < 0L || reconcileAge > FORCE_RESTART_AFTER_MS) return true;
        // Zombie: reconcile still within the force window, but callbacks silent past the long window.
        // Zero-callback grace must also use the long window or quiet phones force-loop at 20s.
        return !callbacksWithinGrace(
                snapshot, now, CALLBACK_SILENCE_FORCE_MS, CALLBACK_SILENCE_FORCE_MS);
    }

    /** UI copy: never claims "active" unless access is granted, health is fresh, and the service is live. */
    public static String statusLabel(
            ListenerHealthStore.Snapshot snapshot,
            long now,
            boolean accessGranted,
            boolean liveServiceConnected) {
        if (!accessGranted) return "Notification access is off";
        if (isFresh(snapshot, now) && liveServiceConnected) return "Notification reader active";
        return "Notification reader reconnecting";
    }

    /**
     * Callbacks are recent enough for {@code maxAgeMs}, or we are still inside
     * {@code zeroCallbackGraceMs} after connect where {@code lastCallbackAt == 0} is allowed.
     */
    private static boolean callbacksWithinGrace(
            ListenerHealthStore.Snapshot snapshot,
            long now,
            long maxAgeMs,
            long zeroCallbackGraceMs) {
        if (snapshot.lastCallbackAt <= 0L) {
            if (snapshot.lastConnectedAt <= 0L) return false;
            long sinceConnected = now - snapshot.lastConnectedAt;
            return sinceConnected >= 0L && sinceConnected <= zeroCallbackGraceMs;
        }
        long callbackAge = now - snapshot.lastCallbackAt;
        return callbackAge >= 0L && callbackAge <= maxAgeMs;
    }
}
