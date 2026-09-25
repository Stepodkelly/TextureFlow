package com.textureflow.intelligence.shield;

/** One event in the §8.1 shape: relative age and untrusted-wrapped text only. */
public final class ShieldedEvent {
    private final int ageMinutes;
    private final String text;

    public ShieldedEvent(int ageMinutes, String text) {
        this.ageMinutes = Math.max(0, ageMinutes);
        this.text = text == null ? "" : text;
    }

    public int getAgeMinutes() {
        return ageMinutes;
    }

    public String getText() {
        return text;
    }
}
