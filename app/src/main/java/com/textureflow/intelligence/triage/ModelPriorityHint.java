package com.textureflow.intelligence.triage;

import java.util.Objects;

/** Model-suggested priority fields used by {@link DeterministicTriage#mergeModelPriority}. */
public final class ModelPriorityHint {
    private final double priorityScore;
    private final String priorityReason;

    public ModelPriorityHint(double priorityScore, String priorityReason) {
        this.priorityScore = priorityScore;
        this.priorityReason = Objects.requireNonNull(priorityReason, "priorityReason");
    }

    public double getPriorityScore() { return priorityScore; }
    public String getPriorityReason() { return priorityReason; }
}
