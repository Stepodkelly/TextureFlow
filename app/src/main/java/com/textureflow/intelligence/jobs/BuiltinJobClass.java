package com.textureflow.intelligence.jobs;

import com.textureflow.intelligence.api.DataClass;
import com.textureflow.intelligence.api.JobClass;

/** Local-only P6 job class. Depth stays off. */
final class BuiltinJobClass implements JobClass {
    private final String id;
    private final int inputTokenBudget;
    private final int outputTokenBudget;
    private final long deadlineMs;
    private final String outputSchema;

    BuiltinJobClass(
            String id,
            int inputTokenBudget,
            int outputTokenBudget,
            long deadlineMs,
            String outputSchema) {
        this.id = id;
        this.inputTokenBudget = inputTokenBudget;
        this.outputTokenBudget = outputTokenBudget;
        this.deadlineMs = deadlineMs;
        this.outputSchema = outputSchema;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public DataClass dataClass() {
        return DataClass.PRIVATE;
    }

    @Override
    public float minEffectiveParamsB() {
        return 0;
    }

    @Override
    public int inputTokenBudget() {
        return inputTokenBudget;
    }

    @Override
    public int outputTokenBudget() {
        return outputTokenBudget;
    }

    @Override
    public long deadlineMs() {
        return deadlineMs;
    }

    @Override
    public boolean allowDepth() {
        return false;
    }

    @Override
    public boolean allowParallelJobs() {
        return false;
    }

    @Override
    public String outputSchema() {
        return outputSchema;
    }

    @Override
    public float exitThresholdOffset() {
        return 0;
    }
}
