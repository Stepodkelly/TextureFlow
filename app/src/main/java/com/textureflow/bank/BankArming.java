package com.textureflow.bank;

import com.textureflow.actions.NotificationControl;

/**
 * Snooze / wake helpers for banked replyable notifications.
 * Arm duration stays under the common 60-minute SystemUI default and within
 * Android's 1–1440 minute snooze policy window.
 */
public final class BankArming {
    /** Rolling hide window: 55 minutes. */
    public static final long DEFAULT_ARM_SNOOZE_MS = 55L * 60L * 1000L;
    /** Brief re-snooze that surfaces the notification for cold-start send. */
    public static final long WAKE_MS = 100L;

    private BankArming() {}

    /** Snooze the entry for {@link #DEFAULT_ARM_SNOOZE_MS}. Never throws across control failures. */
    public static boolean arm(NotificationControl control, BankEntry entry) {
        return snoozeSafe(control, entry, DEFAULT_ARM_SNOOZE_MS);
    }

    /** Same as {@link #arm} — rolling re-snooze on adopt / refresh. */
    public static boolean refreshArm(NotificationControl control, BankEntry entry) {
        return arm(control, entry);
    }

    /** Short snooze so the shade briefly resurfaces the banked notification. */
    public static boolean wake(NotificationControl control, BankEntry entry) {
        return snoozeSafe(control, entry, WAKE_MS);
    }

    private static boolean snoozeSafe(NotificationControl control, BankEntry entry, long durationMs) {
        if (control == null || !control.isAvailable()) return false;
        if (entry == null) return false;
        String key = entry.notificationKey();
        if (key == null || key.isEmpty()) return false;
        try {
            control.snooze(key, durationMs);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
