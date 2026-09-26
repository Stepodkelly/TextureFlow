package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.model.ModelLifecycle;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;

/**
 * Doc §9.4 wizard: 600-token prefill + 64-token decode, then a short draft generate.
 * {@link NoModelPort} and any generate failure become copy — they do not throw out.
 */
public final class PhoneBenchRunner {
    private PhoneBenchRunner() {}

    public static String run(boolean modelFilePresent, CapabilityTier tier, ModelPort port) {
        return runMeasured(modelFilePresent, tier, port, System.currentTimeMillis()).getCopy();
    }

    public static PhoneBenchOutcome runMeasured(
            boolean modelFilePresent, CapabilityTier tier, ModelPort port, long nowMillis) {
        if (!modelFilePresent) {
            return new PhoneBenchOutcome(PhoneBenchCopy.missingFile(tier), null);
        }
        if (port == null || port instanceof NoModelPort) {
            return new PhoneBenchOutcome(PhoneBenchCopy.noPort(tier), null);
        }
        ModelPort lifecycle = port instanceof ModelLifecycle ? port : new ModelLifecycle(port);
        try {
            ModelResponse triage = lifecycle.generate(new ModelRequest(
                    PhoneBenchCopy.prefillPrompt(lifecycle),
                    PhoneBenchCopy.DECODE_TOKENS,
                    0.0f));
            long draftMs = 0L;
            try {
                ModelResponse draft = lifecycle.generate(new ModelRequest(
                        PhoneBenchCopy.DRAFT_PROMPT,
                        PhoneBenchCopy.DRAFT_MAX_TOKENS,
                        0.2f));
                draftMs = draft == null ? 0L : draft.getWallMs();
            } catch (Throwable ignored) {
                // Draft timing is extra; triage numbers still store.
            }
            long triageMs = triage == null ? 0L : triage.getWallMs();
            double tokS = decodeTokS(triage);
            CapabilityProfile.Bench bench = new CapabilityProfile.Bench(
                    tokS, triageMs, triageMs, nowMillis);
            return new PhoneBenchOutcome(
                    PhoneBenchCopy.success(tier, triageMs, draftMs, tokS), bench);
        } catch (Throwable error) {
            return new PhoneBenchOutcome(PhoneBenchCopy.failure(tier, error), null);
        } finally {
            try {
                lifecycle.unload();
            } catch (Throwable ignored) {
                // Unload must not surface as a crash from Settings.
            }
        }
    }

    static double decodeTokS(ModelResponse response) {
        if (response == null || response.getWallMs() <= 0 || response.getOutputTokens() <= 0) {
            return 0.0d;
        }
        return response.getOutputTokens() / (response.getWallMs() / 1000.0d);
    }
}
