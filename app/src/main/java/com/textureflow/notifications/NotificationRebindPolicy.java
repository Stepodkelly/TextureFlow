package com.textureflow.notifications;

final class NotificationRebindPolicy {
    private static final int MAX_EXPONENT = 6;
    private static final long BASE_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 60_000L;

    private NotificationRebindPolicy() {}

    static long delayMillis(int attempt) {
        int exponent = Math.max(0, Math.min(attempt, MAX_EXPONENT));
        return Math.min(MAX_DELAY_MS, BASE_DELAY_MS << exponent);
    }
}
