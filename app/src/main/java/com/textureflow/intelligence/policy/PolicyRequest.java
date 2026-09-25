package com.textureflow.intelligence.policy;

import com.textureflow.intelligence.api.AttentionLevel;

/**
 * Everything PolicyGate needs for one model output. Optional fields are skipped by
 * rules that do not apply (for example triage has no action type).
 */
public final class PolicyRequest {
    private final SchemaName schema;
    private final String modelJson;
    private final String actionType;
    private final String proposalRecipient;
    private final String proposalPackageName;
    private final String eventRecipient;
    private final String eventPackageName;
    private final String eventStatus;
    private final Integer tickEventVersion;
    private final Integer currentEventVersion;
    private final boolean injectionFlagged;
    private final Boolean skepticOk;
    private final String draftText;
    private final String userInstruction;
    private final AttentionLevel proposedLevel;
    private final Double systemConfidence;

    private PolicyRequest(Builder builder) {
        this.schema = builder.schema;
        this.modelJson = builder.modelJson;
        this.actionType = normalize(builder.actionType);
        this.proposalRecipient = blankToEmpty(builder.proposalRecipient);
        this.proposalPackageName = blankToEmpty(builder.proposalPackageName);
        this.eventRecipient = blankToEmpty(builder.eventRecipient);
        this.eventPackageName = blankToEmpty(builder.eventPackageName);
        this.eventStatus = builder.eventStatus;
        this.tickEventVersion = builder.tickEventVersion;
        this.currentEventVersion = builder.currentEventVersion;
        this.injectionFlagged = builder.injectionFlagged;
        this.skepticOk = builder.skepticOk;
        this.draftText = builder.draftText == null ? "" : builder.draftText;
        this.userInstruction = builder.userInstruction == null ? "" : builder.userInstruction;
        this.proposedLevel = builder.proposedLevel;
        this.systemConfidence = builder.systemConfidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public SchemaName schema() {
        return schema;
    }

    public String modelJson() {
        return modelJson;
    }

    public String actionType() {
        return actionType;
    }

    public String proposalRecipient() {
        return proposalRecipient;
    }

    public String proposalPackageName() {
        return proposalPackageName;
    }

    public String eventRecipient() {
        return eventRecipient;
    }

    public String eventPackageName() {
        return eventPackageName;
    }

    public String eventStatus() {
        return eventStatus;
    }

    public Integer tickEventVersion() {
        return tickEventVersion;
    }

    public Integer currentEventVersion() {
        return currentEventVersion;
    }

    public boolean injectionFlagged() {
        return injectionFlagged;
    }

    public Boolean skepticOk() {
        return skepticOk;
    }

    public String draftText() {
        return draftText;
    }

    public String userInstruction() {
        return userInstruction;
    }

    public AttentionLevel proposedLevel() {
        return proposedLevel;
    }

    public Double systemConfidence() {
        return systemConfidence;
    }

    public boolean isReplyDraft() {
        return "REPLY".equals(actionType) || schema == SchemaName.DRAFTER || schema == SchemaName.DRAFT_V1;
    }

    public boolean isActionProposal() {
        return actionType != null && !actionType.isEmpty();
    }

    private static String normalize(String actionType) {
        if (actionType == null || actionType.trim().isEmpty()) {
            return null;
        }
        return actionType.trim().toUpperCase();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private SchemaName schema;
        private String modelJson;
        private String actionType;
        private String proposalRecipient;
        private String proposalPackageName;
        private String eventRecipient;
        private String eventPackageName;
        private String eventStatus;
        private Integer tickEventVersion;
        private Integer currentEventVersion;
        private boolean injectionFlagged;
        private Boolean skepticOk;
        private String draftText;
        private String userInstruction;
        private AttentionLevel proposedLevel;
        private Double systemConfidence;

        public Builder schema(SchemaName schema) {
            this.schema = schema;
            return this;
        }

        public Builder modelJson(String modelJson) {
            this.modelJson = modelJson;
            return this;
        }

        public Builder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder proposalRecipient(String proposalRecipient) {
            this.proposalRecipient = proposalRecipient;
            return this;
        }

        public Builder proposalPackageName(String proposalPackageName) {
            this.proposalPackageName = proposalPackageName;
            return this;
        }

        public Builder eventRecipient(String eventRecipient) {
            this.eventRecipient = eventRecipient;
            return this;
        }

        public Builder eventPackageName(String eventPackageName) {
            this.eventPackageName = eventPackageName;
            return this;
        }

        public Builder eventStatus(String eventStatus) {
            this.eventStatus = eventStatus;
            return this;
        }

        public Builder tickEventVersion(Integer tickEventVersion) {
            this.tickEventVersion = tickEventVersion;
            return this;
        }

        public Builder currentEventVersion(Integer currentEventVersion) {
            this.currentEventVersion = currentEventVersion;
            return this;
        }

        public Builder injectionFlagged(boolean injectionFlagged) {
            this.injectionFlagged = injectionFlagged;
            return this;
        }

        public Builder skepticOk(Boolean skepticOk) {
            this.skepticOk = skepticOk;
            return this;
        }

        public Builder draftText(String draftText) {
            this.draftText = draftText;
            return this;
        }

        public Builder userInstruction(String userInstruction) {
            this.userInstruction = userInstruction;
            return this;
        }

        public Builder proposedLevel(AttentionLevel proposedLevel) {
            this.proposedLevel = proposedLevel;
            return this;
        }

        public Builder systemConfidence(Double systemConfidence) {
            this.systemConfidence = systemConfidence;
            return this;
        }

        public PolicyRequest build() {
            return new PolicyRequest(this);
        }
    }
}
