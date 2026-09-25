package com.textureflow.intelligence.ledger;

import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;

import java.util.Objects;

/**
 * Ledger row for one published assessment.
 * {@code reasonText} is generated (≤ 120 chars) and must not be copied from a message body.
 */
public final class AssessmentRecord {
    public static final int MAX_REASON_TEXT = 120;

    private final String tickId;
    private final AttentionLevel level;
    private final double systemConfidence;
    private final Double modelConfidence;
    private final String reasonCode;
    private final String reasonText;

    public AssessmentRecord(
            String tickId,
            AttentionLevel level,
            double systemConfidence,
            Double modelConfidence,
            String reasonCode,
            String reasonText) {
        this.tickId = Objects.requireNonNull(tickId, "tickId");
        this.level = Objects.requireNonNull(level, "level");
        this.systemConfidence = systemConfidence;
        this.modelConfidence = modelConfidence;
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.reasonText = capReason(Objects.requireNonNull(reasonText, "reasonText"));
    }

    public static AssessmentRecord from(AttentionAssessment assessment, String reasonCode) {
        Objects.requireNonNull(assessment, "assessment");
        return new AssessmentRecord(
                assessment.getTickId(),
                assessment.getLevel(),
                assessment.getSystemConfidence(),
                assessment.getModelConfidence(),
                reasonCode,
                assessment.getReason());
    }

    public String getTickId() { return tickId; }
    public AttentionLevel getLevel() { return level; }
    public double getSystemConfidence() { return systemConfidence; }
    public Double getModelConfidence() { return modelConfidence; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonText() { return reasonText; }

    private static String capReason(String reasonText) {
        if (reasonText.length() <= MAX_REASON_TEXT) {
            return reasonText;
        }
        return reasonText.substring(0, MAX_REASON_TEXT);
    }
}
