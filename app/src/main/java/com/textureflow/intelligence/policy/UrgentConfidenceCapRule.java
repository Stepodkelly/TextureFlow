package com.textureflow.intelligence.policy;

import com.textureflow.intelligence.api.AttentionLevel;

/**
 * §6.4 / §5.4: URGENT with {@code systemConfidence} below the threshold is capped to IMPORTANT.
 * Default threshold is 0.8, matching the deterministic URGENT score cut.
 */
public final class UrgentConfidenceCapRule implements PolicyRule {
    public static final double DEFAULT_THRESHOLD = 0.8d;

    private final double threshold;

    public UrgentConfidenceCapRule() {
        this(DEFAULT_THRESHOLD);
    }

    public UrgentConfidenceCapRule(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String name() {
        return PolicyRules.URGENT_CONFIDENCE_CAP;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (request.proposedLevel() != AttentionLevel.URGENT) {
            return;
        }
        Double confidence = request.systemConfidence();
        if (confidence == null || confidence < threshold) {
            accumulator.capLevel(name(), AttentionLevel.IMPORTANT);
        }
    }
}
