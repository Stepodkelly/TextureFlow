package com.textureflow.actions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ActionCommand {
    public static final int CONTRACT_VERSION = 1;

    private final int contractVersion;
    private final String commandId;
    private final String ownerId;
    private final String proposalId;
    private final String targetDeviceId;
    private final String eventId;
    private final int expectedEventVersion;
    private final ActionType actionType;
    private final Map<String, Object> payload;
    private final String idempotencyKey;
    private final String status;
    private final String createdAt;
    private final String expiresAt;

    public ActionCommand(
            int contractVersion,
            String commandId,
            String ownerId,
            String proposalId,
            String targetDeviceId,
            String eventId,
            int expectedEventVersion,
            ActionType actionType,
            Map<String, Object> payload,
            String idempotencyKey,
            String status,
            String createdAt,
            String expiresAt) {
        this.contractVersion = contractVersion;
        this.commandId = commandId;
        this.ownerId = ownerId;
        this.proposalId = proposalId;
        this.targetDeviceId = targetDeviceId;
        this.eventId = eventId;
        this.expectedEventVersion = expectedEventVersion;
        this.actionType = actionType;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload == null
                ? Collections.emptyMap() : payload));
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getContractVersion() { return contractVersion; }
    public String getCommandId() { return commandId; }
    public String getOwnerId() { return ownerId; }
    public String getProposalId() { return proposalId; }
    public String getTargetDeviceId() { return targetDeviceId; }
    public String getEventId() { return eventId; }
    public int getExpectedEventVersion() { return expectedEventVersion; }
    public ActionType getActionType() { return actionType; }
    public Map<String, Object> getPayload() { return payload; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getExpiresAt() { return expiresAt; }

    public String stringPayload(String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public long longPayload(String key, long fallback) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
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
