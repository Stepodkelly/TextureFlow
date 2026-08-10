package com.textureflow.connection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ConnectionWatchdogTest {
    @Test
    public void healthyLoopNeedsNoRecovery() {
        ConnectionWatchdog watchdog = new ConnectionWatchdog(10_000L, 5_000L);
        assertEquals(
                ConnectionWatchdog.Action.NONE,
                watchdog.inspect(20_000L, 15_000L, true, false).action());
    }

    @Test
    public void missingScheduleIsWoken() {
        ConnectionWatchdog watchdog = new ConnectionWatchdog(10_000L, 5_000L);
        assertEquals(
                ConnectionWatchdog.Action.WAKE,
                watchdog.inspect(20_000L, 1_000L, false, false).action());
    }

    @Test
    public void wedgedOperationRestartsAndRecoveryIsRateLimited() {
        ConnectionWatchdog watchdog = new ConnectionWatchdog(10_000L, 5_000L);
        assertEquals(
                ConnectionWatchdog.Action.RESTART,
                watchdog.inspect(20_000L, 1_000L, true, true).action());
        assertEquals(
                ConnectionWatchdog.Action.NONE,
                watchdog.inspect(21_000L, 1_000L, true, true).action());
        assertEquals(
                ConnectionWatchdog.Action.RESTART,
                watchdog.inspect(26_000L, 1_000L, true, true).action());
    }
}
