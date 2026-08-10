package com.textureflow.connection;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RemoteCommand(
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
        String sourceMode,
        String traceId,
        String createdAt,
        String expiresAt) {

    public RemoteCommand {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload == null
                ? Collections.emptyMap() : payload));
    }

    /** The Android executor accepts CLAIMED evidence even after Core records EXECUTING. */
    public ActionCommand toLocalClaimedCommand() {
        return new ActionCommand(
                contractVersion, commandId, ownerId, proposalId, targetDeviceId, eventId,
                expectedEventVersion, actionType, payload, idempotencyKey, "CLAIMED", createdAt, expiresAt);
    }
}
