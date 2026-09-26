package com.textureflow.notifications;

import static org.junit.Assert.assertEquals;

import com.textureflow.intelligence.ledger.IntelligenceLedger;

import org.junit.Test;

public final class NotificationHealthJobServiceTest {
    @Test
    public void retentionCutoffIsFourteenDaysBeforeNow() {
        long now = 1_700_000_000_000L;
        assertEquals(now - IntelligenceLedger.RETENTION_MILLIS,
                NotificationHealthJobService.retentionCutoff(now));
    }
}
