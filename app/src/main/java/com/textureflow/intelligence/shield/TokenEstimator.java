package com.textureflow.intelligence.shield;

/** Pluggable tokenizer. The default is a chars/4 heuristic until a model port supplies a real one. */
public interface TokenEstimator {
    int estimateTokens(String text);
}
