package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.model.ModelPort;

import java.util.Locale;

/** Doc §9.4 “Test this phone” copy. */
public final class PhoneBenchCopy {
    public static final int PREFILL_TOKENS = 600;
    public static final int DECODE_TOKENS = 64;
    public static final int DRAFT_MAX_TOKENS = 32;
    public static final String DRAFT_PROMPT =
            "Return JSON only: {\"text\":\"thanks\",\"tone\":\"NEUTRAL\"}";

    private PhoneBenchCopy() {}

    public static String prefillPrompt(ModelPort port) {
        String chunk = "alpha ";
        StringBuilder text = new StringBuilder();
        while (tokenCount(port, text) < PREFILL_TOKENS) {
            text.append(chunk);
            if (text.length() > PREFILL_TOKENS * 8) {
                break;
            }
        }
        return text.toString();
    }

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

    public static String success(
            CapabilityTier tier, long triageMs, long draftMs, double decodeTokS) {
        return plainTier(tier)
                + " Prefill+64 decode " + triageMs + " ms"
                + " · draft " + draftMs + " ms"
                + " · " + formatTokS(decodeTokS) + " tok/s.";
    }

    public static String failure(CapabilityTier tier, Throwable error) {
        String detail = error == null || error.getMessage() == null || error.getMessage().isEmpty()
                ? "no model port"
                : error.getMessage();
        return plainTier(tier) + " Wizard could not finish (" + detail + "). Ranking still works.";
    }

    public static String running() {
        return "Testing this phone (600-token prefill + 64-token decode, then a draft)…";
    }

    public static String pipLegend() {
        return "A muted teal pip on a chat means the ranking was refined on this phone, not in the cloud.";
    }

    public static String formatTokS(double tokS) {
        return String.format(Locale.US, "%.2f", tokS);
    }

    static int tokenCount(ModelPort port, CharSequence text) {
        String value = text == null ? "" : text.toString();
        if (port == null) {
            return Math.max(0, (int) Math.ceil(value.length() / 4.0));
        }
        return port.tokenCount(value);
    }
}
