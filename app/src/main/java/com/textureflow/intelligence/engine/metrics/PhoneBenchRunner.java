package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.model.ModelLifecycle;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;

/**
 * Short on-device generate for Settings. Never requires the Xiaomi 10s bench.
 * {@link NoModelPort} and any generate failure become copy — they do not throw out.
 */
public final class PhoneBenchRunner {
    private PhoneBenchRunner() {}

    public static String run(boolean modelFilePresent, CapabilityTier tier, ModelPort port) {
        if (!modelFilePresent) {
            return PhoneBenchCopy.missingFile(tier);
        }
        if (port == null || port instanceof NoModelPort) {
            return PhoneBenchCopy.noPort(tier);
        }
        ModelPort lifecycle = port instanceof ModelLifecycle ? port : new ModelLifecycle(port);
        try {
            ModelResponse response = lifecycle.generate(new ModelRequest(
                    PhoneBenchCopy.SHORT_PROMPT, PhoneBenchCopy.SHORT_MAX_TOKENS, 0.0f));
            return PhoneBenchCopy.success(tier, response);
        } catch (Throwable error) {
            return PhoneBenchCopy.failure(tier, error);
        } finally {
            try {
                lifecycle.unload();
            } catch (Throwable ignored) {
                // Unload must not surface as a crash from Settings.
            }
        }
    }
}
