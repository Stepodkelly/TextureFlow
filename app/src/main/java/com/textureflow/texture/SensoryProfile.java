package com.textureflow.texture;

public enum SensoryProfile {
    BALANCED("Balanced", 0.72f, 0.72f),
    VOICE_FIRST("Voice first", 0.34f, 0.48f),
    HAPTIC_FIRST("Haptic first", 0.24f, 1.0f),
    LOW_STIMULATION("Low stimulation", 0.22f, 0.42f),
    VISUAL_ONLY("Visual only", 0f, 0f);

    private final String displayName;
    private final float audioStrength;
    private final float hapticStrength;

    SensoryProfile(String displayName, float audioStrength, float hapticStrength) {
        this.displayName = displayName;
        this.audioStrength = audioStrength;
        this.hapticStrength = hapticStrength;
    }

    public String displayName() {
        return displayName;
    }

    public float audioStrength() {
        return audioStrength;
    }

    public float hapticStrength() {
        return hapticStrength;
    }

    public boolean allowsAudio(TextureCue cue) {
        if (this == VISUAL_ONLY) {
            return false;
        }
        if (this == LOW_STIMULATION || this == VOICE_FIRST || this == HAPTIC_FIRST) {
            return isEssential(cue);
        }
        return true;
    }

    public boolean allowsHaptics(TextureCue cue) {
        if (this == VISUAL_ONLY) {
            return false;
        }
        if (this == LOW_STIMULATION || this == VOICE_FIRST) {
            return isEssential(cue) || cue == TextureCue.FOCUS_ENTERED;
        }
        return true;
    }

    public boolean reducesContinuousTexture() {
        return this == LOW_STIMULATION || this == VOICE_FIRST || this == VISUAL_ONLY;
    }

    public SensoryProfile next() {
        SensoryProfile[] profiles = values();
        return profiles[(ordinal() + 1) % profiles.length];
    }

    private static boolean isEssential(TextureCue cue) {
        return cue == TextureCue.CONFIRMATION_REQUIRED
                || cue == TextureCue.ACTION_DISPATCHED
                || cue == TextureCue.ACTION_FAILED
                || cue == TextureCue.CANCELLED;
    }
}
