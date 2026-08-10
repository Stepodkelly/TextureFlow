package com.textureflow.notifications;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/** Immutable-enough callback capture processed on TextureFlow's serial worker. */
public final class NotificationSnapshot {
    private final String key;
    private final String packageName;
    private final long postTime;
    private final boolean clearable;
    private final int visibility;
    private final String category;
    private final Bundle extras;
    private final Notification.Action[] actions;

    private NotificationSnapshot(
            String key,
            String packageName,
            long postTime,
            boolean clearable,
            int visibility,
            String category,
            Bundle extras,
            Notification.Action[] actions) {
        this.key = key;
        this.packageName = packageName;
        this.postTime = postTime;
        this.clearable = clearable;
        this.visibility = visibility;
        this.category = category;
        this.extras = extras;
        this.actions = actions;
    }

    public static NotificationSnapshot capture(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null || statusBarNotification.getNotification() == null) {
            throw new IllegalArgumentException("Notification is required");
        }
        Notification notification = statusBarNotification.getNotification();
        Bundle extras;
        try {
            extras = notification.extras == null ? new Bundle() : new Bundle(notification.extras);
        } catch (RuntimeException malformedExtras) {
            extras = new Bundle();
        }
        Notification.Action[] actions = notification.actions == null
                ? new Notification.Action[0] : notification.actions.clone();
        return new NotificationSnapshot(
                statusBarNotification.getKey(), statusBarNotification.getPackageName(),
                statusBarNotification.getPostTime(), statusBarNotification.isClearable(),
                notification.visibility, notification.category, extras, actions);
    }

    public String getKey() { return key; }
    public String getPackageName() { return packageName; }
    public long getPostTime() { return postTime; }
    public boolean isClearable() { return clearable; }
    public int getVisibility() { return visibility; }
    public String getCategory() { return category; }
    public Bundle getExtras() { return new Bundle(extras); }
    public Notification.Action[] getActions() { return actions.clone(); }
}
