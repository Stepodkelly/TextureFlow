package com.textureflow.intelligence.shield;

/** Secondary character bounds from {@code DEFAULT_CONTEXT_LIMITS} in {@code context.ts}. */
public final class ContextLimits {
    public static final ContextLimits DEFAULT = new ContextLimits(5, 600, 2_000);

    private final int maxEvents;
    private final int maxBodyCharactersPerEvent;
    private final int maxTotalBodyCharacters;

    public ContextLimits(int maxEvents, int maxBodyCharactersPerEvent, int maxTotalBodyCharacters) {
        this.maxEvents = clamp(maxEvents, 1, 5);
        this.maxBodyCharactersPerEvent = clamp(maxBodyCharactersPerEvent, 80, 800);
        this.maxTotalBodyCharacters = clamp(maxTotalBodyCharacters, 200, 2_400);
    }

    public int maxEvents() {
        return maxEvents;
    }

    public int maxBodyCharactersPerEvent() {
        return maxBodyCharactersPerEvent;
    }

    public int maxTotalBodyCharacters() {
        return maxTotalBodyCharacters;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
