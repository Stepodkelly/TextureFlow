package com.textureflow.intelligence.model;

import java.util.Objects;

/** One generate call. Prompt is already shielded by the caller. */
public final class ModelRequest {
    private final String prompt;
    private final int maxOutputTokens;
    private final float temperature;

    public ModelRequest(String prompt, int maxOutputTokens, float temperature) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    public String getPrompt() { return prompt; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public float getTemperature() { return temperature; }
}
