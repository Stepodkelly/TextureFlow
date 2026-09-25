package com.textureflow.intelligence.triage;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Minimal event for deterministic triage. No notification key or Android handle.
 * {@code postedAt} may be epoch millis and/or an ISO-8601 string.
 */
public final class TriageEvent {
    private final String eventId;
    private final String packageName;
    private final String body;
    private final Long postedAtMillis;
    private final String postedAtIso;
    private final String senderDisplayName;
    private final String senderPersonId;

    public TriageEvent(
            String eventId,
            String packageName,
            String body,
            Long postedAtMillis,
            String postedAtIso,
            String senderDisplayName,
            String senderPersonId) {
        this.eventId = eventId == null ? "" : eventId;
        this.packageName = packageName == null ? "" : packageName;
        this.body = body == null ? "" : body;
        this.postedAtMillis = postedAtMillis;
        this.postedAtIso = postedAtIso;
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName;
        this.senderPersonId = senderPersonId == null || senderPersonId.isEmpty()
                ? null
                : senderPersonId;
    }

    public static TriageEvent iso(
            String eventId,
            String packageName,
            String body,
            String postedAtIso,
            String senderDisplayName,
            String senderPersonId) {
        return new TriageEvent(
                eventId, packageName, body, null, postedAtIso, senderDisplayName, senderPersonId);
    }

    public static TriageEvent atMillis(
            String eventId,
            String packageName,
            String body,
            long postedAtMillis,
            String senderDisplayName,
            String senderPersonId) {
        return new TriageEvent(
                eventId, packageName, body, postedAtMillis, null, senderDisplayName, senderPersonId);
    }

    public String getEventId() { return eventId; }
    public String getPackageName() { return packageName; }
    public String getBody() { return body; }
    public Long getPostedAtMillis() { return postedAtMillis; }
    public String getPostedAtIso() { return postedAtIso; }
    public String getSenderDisplayName() { return senderDisplayName; }
    public String getSenderPersonId() { return senderPersonId; }

    /**
     * Epoch millis when parseable; {@code null} when both millis and ISO are missing/invalid
     * (TS {@code Date.parse} → NaN → recency 0.3).
     */
    public Long resolvedPostedAtMillis() {
        if (postedAtMillis != null) {
            return postedAtMillis;
        }
        return parseIsoMillis(postedAtIso);
    }

    static Long parseIsoMillis(String postedAt) {
        if (postedAt == null || postedAt.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(postedAt).toInstant().toEpochMilli();
        } catch (DateTimeException ignored) {
            // fall through
        }
        try {
            return Instant.parse(postedAt).toEpochMilli();
        } catch (DateTimeException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(postedAt).toInstant().toEpochMilli();
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "TriageEvent{" + Objects.toString(eventId) + "}";
    }
}
