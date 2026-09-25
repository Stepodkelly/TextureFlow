package com.textureflow.intelligence.shield;

/** Ledger-facing token accounting from doc §8.2. */
public final class TokenBudgetReport {
    private final int inputTokens;
    private final int droppedTokens;
    private final boolean contextOverflow;

    public TokenBudgetReport(int inputTokens, int droppedTokens, boolean contextOverflow) {
        this.inputTokens = Math.max(0, inputTokens);
        this.droppedTokens = Math.max(0, droppedTokens);
        this.contextOverflow = contextOverflow;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int droppedTokens() {
        return droppedTokens;
    }

    public boolean contextOverflow() {
        return contextOverflow;
    }
}
