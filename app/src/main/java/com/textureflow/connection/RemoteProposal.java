package com.textureflow.connection;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RemoteProposal(
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

    public RemoteProposal {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload == null
                ? Collections.emptyMap() : payload));
    }

    public ConfirmedProposal toConfirmedProposal() {
        return new ConfirmedProposal(
                proposalId, ownerId, targetDeviceId, eventId, expectedEventVersion,
                actionType, payload, status, confirmedAt, expiresAt);
    }
}
