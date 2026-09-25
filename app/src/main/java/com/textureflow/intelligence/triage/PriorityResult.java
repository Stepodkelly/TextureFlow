package com.textureflow.intelligence.triage;

import java.util.Objects;

/** Result of {@link DeterministicTriage#assess}. */
public final class PriorityResult {
    private final PriorityAssessment assessment;
    private final PriorityFeatures features;

    public PriorityResult(PriorityAssessment assessment, PriorityFeatures features) {
        this.assessment = Objects.requireNonNull(assessment, "assessment");
        this.features = Objects.requireNonNull(features, "features");
    }

    public PriorityAssessment getAssessment() { return assessment; }
    public PriorityFeatures getFeatures() { return features; }
}
