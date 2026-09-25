package com.textureflow.intelligence.api;

import com.textureflow.actions.ActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Draft the UI may show and confirm. Not an executable command.
 * Confirmation builds {@code ConfirmedProposal} outside this package.
 */
public final class ProposalDraft {
    private final String proposalId;
    private final String eventId;
    private final int eventVersion;
    private final String packageName;
    private final String personId;
    private final String recipient;
    private final ActionType actionType;
    private final String replyText;
    private final ReplyTone tone;
    private final String spokenPreview;
    private final AssessmentSource source;
    private final double confidence;
    private final List<String> ambiguities;
    private final String payloadHash;
    private final long expiresAtMillis;

    public ProposalDraft(
            String proposalId,
            String eventId,
            int eventVersion,
            String packageName,
            String personId,
            String recipient,
            ActionType actionType,
            String replyText,
            ReplyTone tone,
            String spokenPreview,
            AssessmentSource source,
            double confidence,
            List<String> ambiguities,
            String payloadHash,
            long expiresAtMillis) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventVersion = eventVersion;
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.personId = personId == null ? "" : personId;
        this.recipient = recipient == null ? "" : recipient;
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.replyText = replyText == null ? "" : replyText;
        this.tone = tone;
        this.spokenPreview = spokenPreview == null ? "" : spokenPreview;
        this.source = Objects.requireNonNull(source, "source");
        this.confidence = confidence;
        this.ambiguities = copyList(ambiguities);
        this.payloadHash = payloadHash == null ? "" : payloadHash;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getProposalId() { return proposalId; }
    public String getEventId() { return eventId; }
    public int getEventVersion() { return eventVersion; }
    public String getPackageName() { return packageName; }
    public String getPersonId() { return personId; }
    public String getRecipient() { return recipient; }
    public ActionType getActionType() { return actionType; }
    public String getReplyText() { return replyText; }
    public ReplyTone getTone() { return tone; }
    public String getSpokenPreview() { return spokenPreview; }
    public AssessmentSource getSource() { return source; }
    public double getConfidence() { return confidence; }
    public List<String> getAmbiguities() { return ambiguities; }
    public String getPayloadHash() { return payloadHash; }
    public long getExpiresAtMillis() { return expiresAtMillis; }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
