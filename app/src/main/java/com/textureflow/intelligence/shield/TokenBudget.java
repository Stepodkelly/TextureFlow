package com.textureflow.intelligence.shield;

/**
 * Prefill budget from doc §8.2. Trims oldest events first and never cuts mid-codepoint.
 */
public final class TokenBudget {
    public static final int DEFAULT_HEADER_TOKENS = 20;
    public static final int DEFAULT_EVENT_TOKENS = 350;
    public static final int DEFAULT_INSTRUCTION_TOKENS = 40;
    public static final int DEFAULT_PREFILL_TOKENS = 600;

    public static final TokenBudget DEFAULT = new TokenBudget(
            DEFAULT_HEADER_TOKENS,
            DEFAULT_EVENT_TOKENS,
            DEFAULT_INSTRUCTION_TOKENS,
            DEFAULT_PREFILL_TOKENS);

    private final int headerTokens;
    private final int eventTokens;
    private final int instructionTokens;
    private final int prefillTokens;

    public TokenBudget(int headerTokens, int eventTokens, int instructionTokens, int prefillTokens) {
        this.headerTokens = Math.max(0, headerTokens);
        this.eventTokens = Math.max(0, eventTokens);
        this.instructionTokens = Math.max(0, instructionTokens);
        this.prefillTokens = Math.max(0, prefillTokens);
    }

    public int headerTokens() {
        return headerTokens;
    }

    public int eventTokens() {
        return eventTokens;
    }

    public int instructionTokens() {
        return instructionTokens;
    }

    public int prefillTokens() {
        return prefillTokens;
    }

    public int eventTokenRoom(int headerUsed, int instructionUsed) {
        int remainingPrefill = Math.max(0, prefillTokens - headerUsed - instructionUsed);
        return Math.min(eventTokens, remainingPrefill);
    }

    public String fitInstruction(String instruction, TokenEstimator estimator) {
        return truncateToTokens(instruction == null ? "" : instruction, instructionTokens, estimator);
    }

    public String fitHeaderPart(String text, int maxTokens, TokenEstimator estimator) {
        return truncateToTokens(text == null ? "" : text, maxTokens, estimator);
    }

    /** Keeps a prefix whose estimate is ≤ {@code maxTokens}. Never splits a surrogate pair. */
    public static String truncateToTokens(String text, int maxTokens, TokenEstimator estimator) {
        if (text == null || text.isEmpty() || maxTokens <= 0) {
            return maxTokens <= 0 ? "" : (text == null ? "" : text);
        }
        if (estimator.estimateTokens(text) <= maxTokens) {
            return text;
        }
        int lo = 0;
        int hi = TextCleaner.codePointLength(text);
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String candidate = TextCleaner.substringCodePoints(text, 0, mid);
            if (estimator.estimateTokens(candidate) <= maxTokens) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return TextCleaner.substringCodePoints(text, 0, lo);
    }
}
