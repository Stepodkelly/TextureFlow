package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.api.CapabilityProfile;

/**
 * Settings wizard result. {@code bench} is null when the 600+64 run did not finish.
 */
public final class PhoneBenchOutcome {
    private final String copy;
    private final CapabilityProfile.Bench bench;

    public PhoneBenchOutcome(String copy, CapabilityProfile.Bench bench) {
        this.copy = copy == null ? "" : copy;
        this.bench = bench;
    }

    public String getCopy() {
        return copy;
    }

    public CapabilityProfile.Bench getBench() {
        return bench;
    }
}
