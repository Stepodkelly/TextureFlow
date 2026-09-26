package com.textureflow.intelligence.model;

/** Always-available fallback when no model runtime is loaded. */
public final class NoModelPort implements ModelPort {
    @Override
    public void load() {
        // nothing to load
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        throw new IllegalStateException("No on-device model is loaded.");
    }

    @Override
    public void unload() {
        // nothing to unload
    }

    @Override
    public int tokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.codePointCount(0, text.length()) / 4.0));
    }

    @Override
    public String runtimeName() {
        return "none";
    }
}
