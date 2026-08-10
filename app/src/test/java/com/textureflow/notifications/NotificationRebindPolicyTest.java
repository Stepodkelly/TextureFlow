package com.textureflow.notifications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NotificationRebindPolicyTest {
    @Test
    public void backsOffAndCapsAtOneMinute() {
        assertEquals(1_000L, NotificationRebindPolicy.delayMillis(0));
        assertEquals(2_000L, NotificationRebindPolicy.delayMillis(1));
        assertEquals(32_000L, NotificationRebindPolicy.delayMillis(5));
        assertEquals(60_000L, NotificationRebindPolicy.delayMillis(6));
        assertEquals(60_000L, NotificationRebindPolicy.delayMillis(100));
    }

    @Test
    public void treatsNegativeAttemptsAsFirstAttempt() {
        assertEquals(1_000L, NotificationRebindPolicy.delayMillis(-1));
    }
}
