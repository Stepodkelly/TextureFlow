package com.textureflow.intelligence.api;

/**
 * Feature-declared compute request (doc §33). Output is data only — never an action.
 * Depth fields stay unused in v1 ({@code allowDepth()} should be false).
 */
public interface JobClass {
    String id();

    DataClass dataClass();

    /** 0 means the local / deterministic path is enough. */
    float minEffectiveParamsB();

    int inputTokenBudget();

    int outputTokenBudget();

    long deadlineMs();

    boolean allowDepth();

    boolean allowParallelJobs();

    String outputSchema();

    float exitThresholdOffset();
}
