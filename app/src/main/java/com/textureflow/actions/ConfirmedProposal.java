package com.textureflow.actions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Trusted confirmation evidence loaded by the command transport, not model output. */
public final class ConfirmedProposal {
    private final String proposalId;
    private final String ownerId;
    private final String targetDeviceId;
    private final String eventId;
    private final int expectedEventVersion;
    private final ActionType actionType;
    private final Map<String, Object> payload;
    private final String status;
    private final String confirmedAt;
    private final String expiresAt;

    public ConfirmedProposal(
            String proposalId,
            String ownerId,
            String targetDeviceId,
            String eventId,
            int expectedEventVersion,
            ActionType actionType,
            Map<String, Object> payload,
            String status,
            String confirmedAt,
            String expiresAt) {
        if (!"CONFIRMED".equals(status) && !"COMMITTED".equals(status)) {
            throw new IllegalArgumentException("Proposal evidence must be CONFIRMED or COMMITTED");
        }
        this.proposalId = proposalId;
        this.ownerId = ownerId;
        this.targetDeviceId = targetDeviceId;
        this.eventId = eventId;
        this.expectedEventVersion = expectedEventVersion;
        this.actionType = actionType;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload == null
                ? Collections.emptyMap() : payload));
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.expiresAt = expiresAt;
    }

    public String getProposalId() { return proposalId; }
    public String getOwnerId() { return ownerId; }
    public String getTargetDeviceId() { return targetDeviceId; }
    public String getEventId() { return eventId; }
    public int getExpectedEventVersion() { return expectedEventVersion; }
    public ActionType getActionType() { return actionType; }
    public Map<String, Object> getPayload() { return payload; }
    public String getStatus() { return status; }
    public String getConfirmedAt() { return confirmedAt; }
    public String getExpiresAt() { return expiresAt; }

    public String stringPayload(String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public long longPayload(String key, long fallback) {
        Object value = payload.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
