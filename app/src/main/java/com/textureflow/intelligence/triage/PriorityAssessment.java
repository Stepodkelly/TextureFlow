package com.textureflow.intelligence.triage;

import com.textureflow.intelligence.api.AttentionLevel;

import java.util.Objects;

/**
 * Score/level/reason triple matching {@code shared/contracts} AttentionAssessment.
 * Distinct from the frozen UI {@link com.textureflow.intelligence.api.AttentionAssessment}.
 */
public final class PriorityAssessment {
    private final double score;
    private final AttentionLevel level;
    private final String reason;

    public PriorityAssessment(double score, AttentionLevel level, String reason) {
        this.score = score;
        this.level = Objects.requireNonNull(level, "level");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public double getScore() { return score; }
    public AttentionLevel getLevel() { return level; }
    public String getReason() { return reason; }
}
