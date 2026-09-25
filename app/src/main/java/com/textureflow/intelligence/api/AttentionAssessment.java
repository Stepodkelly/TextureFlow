package com.textureflow.intelligence.api;

import java.util.Objects;

/** Per-person assessment published to the UI. */
public final class AttentionAssessment {
    private final String tickId;
    private final String personId;
    private final String packageName;
    private final String leadEventId;
    private final int eventVersion;
    private final double score;
    private final AttentionLevel level;
    private final String reason;
    private final double systemConfidence;
    private final Double modelConfidence;
    private final AssessmentSource source;

    public AttentionAssessment(
            String tickId,
            String personId,
            String packageName,
            String leadEventId,
            int eventVersion,
            double score,
            AttentionLevel level,
            String reason,
            double systemConfidence,
            Double modelConfidence,
            AssessmentSource source) {
        this.tickId = Objects.requireNonNull(tickId, "tickId");
        this.personId = personId == null ? "" : personId;
        this.packageName = packageName == null ? "" : packageName;
        this.leadEventId = leadEventId == null ? "" : leadEventId;
        this.eventVersion = eventVersion;
        this.score = score;
        this.level = Objects.requireNonNull(level, "level");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.systemConfidence = systemConfidence;
        this.modelConfidence = modelConfidence;
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getTickId() { return tickId; }
    public String getPersonId() { return personId; }
    public String getPackageName() { return packageName; }
    public String getLeadEventId() { return leadEventId; }
    public int getEventVersion() { return eventVersion; }
    public double getScore() { return score; }
    public AttentionLevel getLevel() { return level; }
    public String getReason() { return reason; }
    public double getSystemConfidence() { return systemConfidence; }
    public Double getModelConfidence() { return modelConfidence; }
    public AssessmentSource getSource() { return source; }
}
