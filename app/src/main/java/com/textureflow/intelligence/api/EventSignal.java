package com.textureflow.intelligence.api;

import java.util.Objects;

/** Capture-plane signal. Identifies an event, never a live notification handle. */
public final class EventSignal {
    public enum Kind {
        POSTED,
        UPDATED,
        REMOVED
    }

    private final Kind kind;
    private final String eventId;
    private final int eventVersion;
    private final String personId;
    private final String packageName;

    public EventSignal(
            Kind kind,
            String eventId,
            int eventVersion,
            String personId,
            String packageName) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventVersion = eventVersion;
        this.personId = personId == null ? "" : personId;
        this.packageName = packageName == null ? "" : packageName;
    }

    public Kind getKind() { return kind; }
    public String getEventId() { return eventId; }
    public int getEventVersion() { return eventVersion; }
    public String getPersonId() { return personId; }
    public String getPackageName() { return packageName; }
}
