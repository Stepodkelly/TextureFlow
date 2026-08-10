package com.textureflow.actions;

import android.app.Notification;

import com.textureflow.notifications.NotificationSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** PendingIntents and raw Android notification keys remain private to this in-process registry. */
public final class LiveActionRegistry {
    public static final class Entry {
        private final String eventId;
        private final int eventVersion;
        private final String notificationKey;
        private final String packageName;
        private final String actionFingerprint;
        private final Notification.Action replyAction;
        private final boolean dismissSupported;
        private final boolean snoozeSupported;

        private Entry(
                String eventId,
                int eventVersion,
                String notificationKey,
                String packageName,
                String actionFingerprint,
                Notification.Action replyAction,
                boolean dismissSupported,
                boolean snoozeSupported) {
            this.eventId = eventId;
            this.eventVersion = eventVersion;
            this.notificationKey = notificationKey;
            this.packageName = packageName;
            this.actionFingerprint = actionFingerprint;
            this.replyAction = replyAction;
            this.dismissSupported = dismissSupported;
            this.snoozeSupported = snoozeSupported;
        }

        public String getEventId() { return eventId; }
        public int getEventVersion() { return eventVersion; }
        public String getNotificationKey() { return notificationKey; }
        public String getPackageName() { return packageName; }
        public String getActionFingerprint() { return actionFingerprint; }
        public Notification.Action getReplyAction() { return replyAction; }
        public boolean isDismissSupported() { return dismissSupported; }
        public boolean isSnoozeSupported() { return snoozeSupported; }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry createEntry(
            String eventId, int eventVersion, String actionFingerprint, NotificationSnapshot snapshot) {
        Notification.Action reply = NotificationActionInspector.findReplyAction(snapshot.getActions());
        return new Entry(
                eventId, eventVersion, snapshot.getKey(), snapshot.getPackageName(), actionFingerprint,
                reply, snapshot.isClearable(), snapshot.isClearable());
    }

    public void put(Entry entry) {
        entries.put(entry.getEventId(), entry);
    }

    public Entry get(String eventId) {
        return entries.get(eventId);
    }

    public void remove(String eventId) {
        entries.remove(eventId);
    }

    public void replaceAll(Map<String, Entry> rebuilt) {
        entries.clear();
        entries.putAll(rebuilt);
    }

    public Map<String, Entry> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(entries));
    }
}
