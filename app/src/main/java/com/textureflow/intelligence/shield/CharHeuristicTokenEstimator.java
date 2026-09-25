package com.textureflow.intelligence.shield;

/** Default estimator: {@code ceil(codePoints / 4)}. Counts Unicode scalar values, not UTF-16 units. */
public final class CharHeuristicTokenEstimator implements TokenEstimator {
    public static final CharHeuristicTokenEstimator INSTANCE = new CharHeuristicTokenEstimator();

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int codePoints = TextCleaner.codePointLength(text);
        return (codePoints + 3) / 4;
    }
}
