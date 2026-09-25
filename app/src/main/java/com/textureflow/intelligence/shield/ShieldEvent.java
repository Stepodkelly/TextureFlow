package com.textureflow.intelligence.shield;

import java.util.Objects;

/** Input event for {@link ContextShield}. Notification keys are accepted and then dropped. */
public final class ShieldEvent {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";

    private final String eventId;
    private final int eventVersion;
    private final String personId;
    private final String packageName;
    private final String appLabel;
    private final String senderDisplayName;
    private final String body;
    private final long postedAtMillis;
    private final String status;
    private final String notificationKey;

    public ShieldEvent(
            String eventId,
            int eventVersion,
            String personId,
            String packageName,
            String appLabel,
            String senderDisplayName,
            String body,
            long postedAtMillis,
            String status,
            String notificationKey) {
        this.eventId = eventId == null ? "" : eventId;
        this.eventVersion = eventVersion;
        this.personId = personId == null ? "" : personId;
        this.packageName = packageName == null ? "" : packageName;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName;
        this.body = body == null ? "" : body;
        this.postedAtMillis = postedAtMillis;
        this.status = status == null ? STATUS_ACTIVE : status;
        this.notificationKey = notificationKey == null ? "" : notificationKey;
    }

    public String getEventId() {
        return eventId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getPersonId() {
        return personId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public String getSenderDisplayName() {
        return senderDisplayName;
    }

    public String getBody() {
        return body;
    }

    public long getPostedAtMillis() {
        return postedAtMillis;
    }

    public String getStatus() {
        return status;
    }

    public String getNotificationKey() {
        return notificationKey;
    }

    public boolean isRemoved() {
        return STATUS_REMOVED.equalsIgnoreCase(status);
    }

    public boolean belongsTo(String targetPersonId) {
        if (targetPersonId == null || targetPersonId.isEmpty()) {
            return true;
        }
        return Objects.equals(personId, targetPersonId);
    }
}
