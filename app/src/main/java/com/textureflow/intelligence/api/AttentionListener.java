package com.textureflow.intelligence.api;

/** UI / voice subscribe to assessments. No action surface. */
public interface AttentionListener {
    void onAssessment(AttentionAssessment assessment);

    void onProposalInvalidated(String proposalId, String reason);
}
