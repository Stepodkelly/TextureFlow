package com.textureflow.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.actions.NotificationControl;

import org.junit.Test;

public final class BankArmingTest {
    @Test
    public void wakeAndArmConstants() {
        assertEquals(100L, BankArming.WAKE_MS);
        assertEquals(55L * 60L * 1000L, BankArming.DEFAULT_ARM_SNOOZE_MS);
        assertTrue(BankArming.DEFAULT_ARM_SNOOZE_MS < 60L * 60L * 1000L);
        assertTrue(BankArming.WAKE_MS > 0L);
    }

    @Test
    public void armReturnsFalseWhenControlUnavailableOrKeyEmpty() {
        BankEntry entry = sample("key-1");
        assertFalse(BankArming.arm(null, entry));
        assertFalse(BankArming.arm(unavailable(), entry));
        assertFalse(BankArming.arm(availableRecording(), sample("")));
        assertFalse(BankArming.wake(availableRecording(), null));
    }

    @Test
    public void armAndWakeInvokeSnoozeWithExpectedDurations() {
        RecordingControl control = availableRecording();
        BankEntry entry = sample("notif-key");
        assertTrue(BankArming.refreshArm(control, entry));
        assertEquals("notif-key", control.lastKey);
        assertEquals(BankArming.DEFAULT_ARM_SNOOZE_MS, control.lastDurationMs);
        assertTrue(BankArming.wake(control, entry));
        assertEquals(BankArming.WAKE_MS, control.lastDurationMs);
    }

    @Test
    public void armSwallowsControlExceptions() {
        NotificationControl exploding = new NotificationControl() {
            @Override public boolean isAvailable() { return true; }
            @Override public void dismiss(String notificationKey) {}
            @Override public void snooze(String notificationKey, long durationMs) {
                throw new IllegalStateException("disconnected mid-snooze");
            }
        };
        assertFalse(BankArming.arm(exploding, sample("k")));
    }

    private static BankEntry sample(String notificationKey) {
        return new BankEntry(
                new BankKey("Alex", "com.whatsapp"),
                "event-1",
                notificationKey,
                "Alex",
                null,
                "hi",
                1L);
    }

    private static NotificationControl unavailable() {
        return new NotificationControl() {
            @Override public boolean isAvailable() { return false; }
            @Override public void dismiss(String notificationKey) {}
            @Override public void snooze(String notificationKey, long durationMs) {}
        };
    }

    private static RecordingControl availableRecording() {
        return new RecordingControl();
    }

    private static final class RecordingControl implements NotificationControl {
        String lastKey;
        long lastDurationMs;

        @Override public boolean isAvailable() { return true; }
        @Override public void dismiss(String notificationKey) {}
        @Override public void snooze(String notificationKey, long durationMs) {
            lastKey = notificationKey;
            lastDurationMs = durationMs;
        }
    }
}
