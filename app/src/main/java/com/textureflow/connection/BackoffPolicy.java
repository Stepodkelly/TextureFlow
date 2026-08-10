package com.textureflow.connection;

public final class BackoffPolicy {
    private final long initialDelayMs;
    private final long maximumDelayMs;
    private final double jitterFraction;

    public BackoffPolicy(long initialDelayMs, long maximumDelayMs, double jitterFraction) {
        if (initialDelayMs < 1 || maximumDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("Backoff bounds are invalid");
        }
        if (jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("Jitter must be between 0 and 0.5");
        }
        this.initialDelayMs = initialDelayMs;
        this.maximumDelayMs = maximumDelayMs;
        this.jitterFraction = jitterFraction;
    }

    public long delayMillis(int failureCount, double randomUnit) {
        int exponent = Math.max(0, Math.min(failureCount - 1, 30));
        long exponential;
        if (initialDelayMs > (Long.MAX_VALUE >> exponent)) {
            exponential = maximumDelayMs;
        } else {
            exponential = Math.min(maximumDelayMs, initialDelayMs << exponent);
        }
        double boundedRandom = Math.max(0.0, Math.min(1.0, randomUnit));
        double multiplier = 1.0 - jitterFraction + (2.0 * jitterFraction * boundedRandom);
        return Math.max(1L, Math.min(maximumDelayMs, Math.round(exponential * multiplier)));
    }
}
