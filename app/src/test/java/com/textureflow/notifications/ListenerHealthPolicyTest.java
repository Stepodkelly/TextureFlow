package com.textureflow.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.data.ListenerHealthStore;

import org.junit.Test;

public final class ListenerHealthPolicyTest {
    // Large enough that CALLBACK_SILENCE_FORCE_MS offsets stay positive (zero-callback path).
    private static final long NOW = 1_000_000L;

    @Test
    public void isFreshRequiresConnectedRecentReconcileAndCallbacks() {
        long now = NOW;
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(false, now - 1_000L, now - 1_000L, now - 1_000L), now));
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 1_000L, now - 1_000L, 0L), now));
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 1_000L, now - 1_000L,
                        now - ListenerHealthPolicy.STALE_AFTER_MS - 1L), now));
        assertTrue(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 1_000L, now - 1_000L,
                        now - ListenerHealthPolicy.STALE_AFTER_MS), now));
        assertTrue(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 1_000L, now - 1_000L, now - 1_000L), now));
    }

    @Test
    public void isFreshAllowsZeroCallbacksOnlyDuringConnectGrace() {
        long now = NOW;
        // No callbacks yet, but within STALE of connect → fresh if reconcile is recent.
        assertTrue(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 5_000L, 0L, now - 1_000L), now));
        // Past connect grace with still-zero callbacks → not fresh (UI).
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(true, now - ListenerHealthPolicy.STALE_AFTER_MS - 1L, 0L, now - 1_000L),
                now));
        // Force must NOT fire at the UI grace boundary on a quiet phone.
        assertFalse(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - ListenerHealthPolicy.STALE_AFTER_MS - 1L, 0L, now - 1_000L),
                now, true));
        // Never got callbacks past the long silence window → force.
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - ListenerHealthPolicy.CALLBACK_SILENCE_FORCE_MS - 1L, 0L,
                        now - 1_000L),
                now, true));
        // Missing connect timestamp with zero callbacks → not fresh.
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(true, 0L, 0L, now - 1_000L), now));
    }

    @Test
    public void isFreshRejectsStaleCallbacksEvenWhenReconcileIsRecent() {
        long now = NOW;
        // Zombie: self-health keeps lastReconciledAt fresh while callbacks are dead for UI window.
        assertFalse(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 60_000L,
                        now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS - 1L,
                        now - 1_000L),
                now));
        // Callbacks at the FORCE boundary are still accepted for UI freshness.
        assertTrue(ListenerHealthPolicy.isFresh(
                snapshot(true, now - 60_000L,
                        now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS,
                        now - 1_000L),
                now));
    }

    @Test
    public void needsForceRestartWhenDeadOrPastForceWindow() {
        long now = NOW;
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 1_000L, now - 1_000L, now - 1_000L), now, false));
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 1_000L, now - 1_000L, 0L), now, true));
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(false, now - 1_000L, now - 1_000L, now - 1_000L), now, true));
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 1_000L, now - 1_000L,
                        now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS - 1L), now, true));
        assertFalse(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 1_000L, now - 1_000L,
                        now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS), now, true));
        assertFalse(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 1_000L, now - 1_000L, now - 1_000L), now, true));
    }

    @Test
    public void needsForceRestartWhenCallbacksSilentPastLongWindow() {
        long now = NOW;
        // UI-stale callbacks (45s+) must NOT force yet — quiet phones.
        assertFalse(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 60_000L,
                        now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS - 1L,
                        now - 1_000L),
                now, true));
        // Past CALLBACK_SILENCE_FORCE_MS → force zombie tear-down.
        assertTrue(ListenerHealthPolicy.needsForceRestart(
                snapshot(true, now - 60_000L,
                        now - ListenerHealthPolicy.CALLBACK_SILENCE_FORCE_MS - 1L,
                        now - 1_000L),
                now, true));
    }

    @Test
    public void probeLooksLockedOutMatchesForceRestartRules() {
        long now = NOW;
        ListenerHealthStore.Snapshot zombie = snapshot(
                true, now - 60_000L,
                now - ListenerHealthPolicy.CALLBACK_SILENCE_FORCE_MS - 1L,
                now - 1_000L);
        assertTrue(ListenerHealthPolicy.probeLooksLockedOut(zombie, now, true));
        assertTrue(ListenerHealthPolicy.needsForceRestart(zombie, now, true));

        ListenerHealthStore.Snapshot healthy = snapshot(
                true, now - 5_000L, now - 2_000L, now - 1_000L);
        assertFalse(ListenerHealthPolicy.probeLooksLockedOut(healthy, now, true));
        assertFalse(ListenerHealthPolicy.needsForceRestart(healthy, now, true));
    }

    @Test
    public void statusLabelNeverClaimsActiveUnlessFreshAndLive() {
        long now = NOW;
        ListenerHealthStore.Snapshot fresh = snapshot(
                true, now - 1_000L, now - 1_000L, now - 1_000L);
        ListenerHealthStore.Snapshot stale = snapshot(
                true, now - 30_000L, now - 30_000L, now - 30_000L);
        ListenerHealthStore.Snapshot zombie = snapshot(
                true, now - 60_000L,
                now - ListenerHealthPolicy.FORCE_RESTART_AFTER_MS - 1L,
                now - 1_000L);

        assertEquals(
                "Notification access is off",
                ListenerHealthPolicy.statusLabel(fresh, now, false, true));
        assertEquals(
                "Notification reader active",
                ListenerHealthPolicy.statusLabel(fresh, now, true, true));
        assertEquals(
                "Notification reader reconnecting",
                ListenerHealthPolicy.statusLabel(fresh, now, true, false));
        assertEquals(
                "Notification reader reconnecting",
                ListenerHealthPolicy.statusLabel(stale, now, true, true));
        assertEquals(
                "Notification reader reconnecting",
                ListenerHealthPolicy.statusLabel(zombie, now, true, true));
        assertEquals(
                "Notification reader reconnecting",
                ListenerHealthPolicy.statusLabel(
                        snapshot(false, now - 1_000L, now - 1_000L, now - 1_000L),
                        now, true, true));
    }

    private static ListenerHealthStore.Snapshot snapshot(
            boolean connected, long lastConnectedAt, long lastCallbackAt, long lastReconciledAt) {
        return new ListenerHealthStore.Snapshot(
                connected, lastConnectedAt, lastCallbackAt, lastReconciledAt, 1, 0, null);
    }
}
