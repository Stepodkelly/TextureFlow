package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.shield.ShieldEvent;
import com.textureflow.intelligence.triage.TriageEvent;

/**
 * Engine-facing event. No notification key, {@code PendingIntent}, or Android type.
 * {@link EventStore} implementations map repository rows onto this shape.
 */
public final class StoredEvent {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_UPDATED = "UPDATED";
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

    public StoredEvent(
            String eventId,
            int eventVersion,
            String personId,
            String packageName,
            String appLabel,
            String senderDisplayName,
            String body,
            long postedAtMillis,
            String status) {
        this.eventId = eventId == null ? "" : eventId;
        this.eventVersion = eventVersion;
        this.personId = personId == null ? "" : personId;
        this.packageName = packageName == null ? "" : packageName;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName;
        this.body = body == null ? "" : body;
        this.postedAtMillis = postedAtMillis;
        this.status = status == null ? STATUS_ACTIVE : status;
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

    public boolean isLive() {
        return !STATUS_REMOVED.equalsIgnoreCase(status);
    }

    public TriageEvent toTriageEvent() {
        return new TriageEvent(
                eventId, packageName, body, postedAtMillis, null, senderDisplayName, personId);
    }

    public ShieldEvent toShieldEvent() {
        return new ShieldEvent(
                eventId,
                eventVersion,
                personId,
                packageName,
                appLabel,
                senderDisplayName,
                body,
                postedAtMillis,
                status,
                "");
    }
}
