package com.textureflow.intelligence.model;

import java.util.Objects;

public final class ModelResponse {
    private final String text;
    private final int inputTokens;
    private final int outputTokens;
    private final long wallMs;

    public ModelResponse(String text, int inputTokens, int outputTokens, long wallMs) {
        this.text = Objects.requireNonNull(text, "text");
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.wallMs = wallMs;
    }

    public String getText() { return text; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getWallMs() { return wallMs; }

    public double decodeTokPerSec() {
        if (wallMs <= 0 || outputTokens <= 0) {
            return 0;
        }
        return outputTokens * 1000.0 / wallMs;
    }
}
