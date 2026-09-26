package com.textureflow.notifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.EventSignal;

import org.junit.Test;

public final class NotificationRuntimeTest {
    @Test
    public void enqueueSkipsWhenIntelligenceModeIsOff() {
        EventSignal signal = new EventSignal(EventSignal.Kind.POSTED, "e1", 1, "sam", "com.whatsapp");
        assertTrue(NotificationRuntime.shouldEnqueue(signal, true));
        assertFalse(NotificationRuntime.shouldEnqueue(signal, false));
        assertFalse(NotificationRuntime.shouldEnqueue(null, true));
    }
}
