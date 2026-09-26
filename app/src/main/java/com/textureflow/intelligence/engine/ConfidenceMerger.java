package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.triage.PriorityAssessment;
import com.textureflow.intelligence.triage.PriorityFeatures;

import java.util.Objects;

/**
 * Doc §5.4. {@code systemConfidence} is engine-computed. Only it can raise a
 * level to {@link AttentionLevel#URGENT}. A model URGENT with no urgency
 * signals is capped at IMPORTANT.
 */
public final class ConfidenceMerger {
    public static final double URGENT_SYSTEM_THRESHOLD = 0.8d;

    private ConfidenceMerger() {}

    public static Result merge(
            PriorityAssessment deterministic,
            PriorityFeatures features,
            AttentionLevel modelLevel,
            Double modelConfidence,
            boolean modelAccepted) {
        Objects.requireNonNull(deterministic, "deterministic");
        Objects.requireNonNull(features, "features");

        double system = systemConfidence(deterministic, modelLevel, modelAccepted);
        AttentionLevel level = deterministic.getLevel();
        String reason = deterministic.getReason();

        if (modelAccepted && modelLevel != null) {
            if (modelLevel == AttentionLevel.URGENT) {
                if (features.getUrgencySignals() <= 0) {
                    level = AttentionLevel.IMPORTANT;
                } else if (system >= URGENT_SYSTEM_THRESHOLD) {
                    level = AttentionLevel.URGENT;
                } else {
                    level = AttentionLevel.IMPORTANT;
                }
            } else {
                level = modelLevel;
            }
        }

        if (level == AttentionLevel.URGENT && !systemAllowsUrgent(system, features)) {
            level = AttentionLevel.IMPORTANT;
        }
        return new Result(level, deterministic.getScore(), system, modelConfidence, reason);
    }

    static double systemConfidence(
            PriorityAssessment deterministic,
            AttentionLevel modelLevel,
            boolean modelAccepted) {
        double base = clamp(deterministic.getScore());
        if (!modelAccepted || modelLevel == null) {
            return base;
        }
        if (modelLevel == deterministic.getLevel()) {
            return clamp(base + 0.05d);
        }
        return clamp(base - 0.05d);
    }

    static boolean systemAllowsUrgent(double systemConfidence, PriorityFeatures features) {
        return systemConfidence >= URGENT_SYSTEM_THRESHOLD && features.getUrgencySignals() > 0;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public static final class Result {
        private final AttentionLevel level;
        private final double score;
        private final double systemConfidence;
        private final Double modelConfidence;
        private final String reason;

        Result(
                AttentionLevel level,
                double score,
                double systemConfidence,
                Double modelConfidence,
                String reason) {
            this.level = level;
            this.score = score;
            this.systemConfidence = systemConfidence;
            this.modelConfidence = modelConfidence;
            this.reason = reason;
        }

        public AttentionLevel getLevel() {
            return level;
        }

        public double getScore() {
            return score;
        }

        public double getSystemConfidence() {
            return systemConfidence;
        }

        public Double getModelConfidence() {
            return modelConfidence;
        }

        public String getReason() {
            return reason;
        }
    }
}
