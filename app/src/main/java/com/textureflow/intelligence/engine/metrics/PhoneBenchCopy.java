package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.model.ModelResponse;

/** Doc §9.4 “Test this phone” copy. Short generate only — not the 10s Xiaomi bench. */
public final class PhoneBenchCopy {
    public static final String SHORT_PROMPT = "Say ok.";
    public static final int SHORT_MAX_TOKENS = 8;

    private PhoneBenchCopy() {}

    public static String plainTier(CapabilityTier tier) {
        if (tier == null) {
            return "Basic: ranking only on this phone.";
        }
        return switch (tier) {
            case T0 -> "Basic: ranking only on this phone.";
            case T1 -> "Slow: triage only on this phone.";
            case T2 -> "Good: summaries and drafts on this phone.";
            case T3 -> "Fast: this phone can run roles in parallel.";
        };
    }

    public static String missingFile() {
        return "The on-device model file is not on this phone yet. Download it above ("
                + ModelDownloadCopy.sizeLabel() + ", Wi-Fi only).";
    }

    public static String missingFile(CapabilityTier tier) {
        return plainTier(tier) + " " + missingFile();
    }

    public static String noPort(CapabilityTier tier) {
        return plainTier(tier)
                + " No model runtime is available, so this phone stays on ranking only.";
    }

    public static String success(CapabilityTier tier, ModelResponse response) {
        long ms = response == null ? 0L : response.getWallMs();
        return plainTier(tier) + " Short generate finished in " + ms + " ms.";
    }

    public static String failure(CapabilityTier tier, Throwable error) {
        String detail = error == null || error.getMessage() == null || error.getMessage().isEmpty()
                ? "no model port"
                : error.getMessage();
        return plainTier(tier) + " Short generate could not run (" + detail + "). Ranking still works.";
    }

    public static String running() {
        return "Testing this phone…";
    }

    public static String pipLegend() {
        return "A muted teal pip on a chat means the ranking was refined on this phone, not in the cloud.";
    }
}
