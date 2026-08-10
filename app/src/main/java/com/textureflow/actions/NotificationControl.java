package com.textureflow.actions;

public interface NotificationControl {
    boolean isAvailable();
    void dismiss(String notificationKey);
    void snooze(String notificationKey, long durationMs);
}
