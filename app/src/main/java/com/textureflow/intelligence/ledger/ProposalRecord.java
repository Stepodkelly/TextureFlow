package com.textureflow.intelligence.ledger;

import com.textureflow.actions.ActionType;
import com.textureflow.intelligence.api.ProposalDraft;

import java.util.Objects;

/**
 * Ledger row for a proposal. Payload text is allowed only while {@link ProposalStatus#OPEN};
 * terminal statuses store an empty payload.
 */
public final class ProposalRecord {
    private final String proposalId;
    private final String eventId;
    private final int eventVersion;
    private final String packageName;
    private final String personId;
    private final ActionType actionType;
    private final String payloadHash;
    private final ProposalStatus status;
    private final long createdAt;
    private final long expiresAt;
    private final String payloadText;

    public ProposalRecord(
            String proposalId,
            String eventId,
            int eventVersion,
            String packageName,
            String personId,
            ActionType actionType,
            String payloadHash,
            ProposalStatus status,
            long createdAt,
            long expiresAt,
            String payloadText) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventVersion = eventVersion;
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.personId = personId == null ? "" : personId;
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.payloadHash = payloadHash == null ? "" : payloadHash;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.payloadText = status.isTerminal() || payloadText == null ? "" : payloadText;
    }

    public static ProposalRecord from(ProposalDraft draft, ProposalStatus status, long createdAt) {
        Objects.requireNonNull(draft, "draft");
        return new ProposalRecord(
                draft.getProposalId(),
                draft.getEventId(),
                draft.getEventVersion(),
                draft.getPackageName(),
                draft.getPersonId(),
                draft.getActionType(),
                draft.getPayloadHash(),
                status,
                createdAt,
                draft.getExpiresAtMillis(),
                draft.getReplyText());
    }

    public ProposalRecord withStatus(ProposalStatus newStatus) {
        return new ProposalRecord(
                proposalId,
                eventId,
                eventVersion,
                packageName,
                personId,
                actionType,
                payloadHash,
                newStatus,
                createdAt,
                expiresAt,
                newStatus.isTerminal() ? "" : payloadText);
    }

    public String getProposalId() { return proposalId; }
    public String getEventId() { return eventId; }
    public int getEventVersion() { return eventVersion; }
    public String getPackageName() { return packageName; }
    public String getPersonId() { return personId; }
    public ActionType getActionType() { return actionType; }
    public String getPayloadHash() { return payloadHash; }
    public ProposalStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public String getPayloadText() { return payloadText; }
}
