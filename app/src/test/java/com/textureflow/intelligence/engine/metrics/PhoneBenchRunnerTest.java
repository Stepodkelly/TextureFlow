package com.textureflow.intelligence.engine.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;

import org.junit.Test;

public final class PhoneBenchRunnerTest {
    @Test
    public void missingFilePointsAtDownload() {
        String text = PhoneBenchRunner.run(false, CapabilityTier.T0, new NoModelPort());
        assertTrue(text.contains("Download"));
        assertTrue(text.contains("~529 MB"));
        assertTrue(text.contains("Basic:"));
    }

    @Test
    public void noModelPortDoesNotThrow() {
        String text = PhoneBenchRunner.run(true, CapabilityTier.T1, new NoModelPort());
        assertTrue(text.contains("Slow:"));
        assertTrue(text.toLowerCase().contains("ranking"));
        assertFalse(text.contains("IllegalStateException"));
    }

    @Test
    public void nullPortDoesNotThrow() {
        String text = PhoneBenchRunner.run(true, CapabilityTier.T2, null);
        assertTrue(text.contains("Good:"));
        assertTrue(text.contains("No model runtime"));
    }

    @Test
    public void wizardReportsPrefillDraftAndTokS() {
        ScriptedPort port = new ScriptedPort(
                new ModelResponse("ok", 600, 29, 10_000),
                new ModelResponse("thanks", 12, 4, 400));
        PhoneBenchOutcome outcome = PhoneBenchRunner.runMeasured(
                true, CapabilityTier.T3, port, 1_700_000_000_000L);
        assertEquals(
                "Fast: this phone can run roles in parallel. Prefill+64 decode 10000 ms · draft 400 ms · 2.90 tok/s.",
                outcome.getCopy());
        assertEquals(2.90d, outcome.getBench().getDecodeTokS(), 0.01d);
        assertEquals(10_000L, outcome.getBench().getPrefill600Ms());
        assertEquals(1_700_000_000_000L, outcome.getBench().getBenchmarkedAt());
        assertEquals(2, port.generates);
    }

    @Test
    public void generateFailureBecomesCopy() {
        String text = PhoneBenchRunner.run(true, CapabilityTier.T0, new ScriptedPort(
                null, new IllegalStateException("No on-device model is loaded.")));
        assertTrue(text.contains("could not finish"));
        assertTrue(text.contains("No on-device model is loaded."));
        assertTrue(text.contains("Ranking still works"));
    }

    @Test
    public void prefillPromptReachesSixHundredTokens() {
        NoModelPort port = new NoModelPort();
        String prompt = PhoneBenchCopy.prefillPrompt(port);
        assertTrue(port.tokenCount(prompt) >= PhoneBenchCopy.PREFILL_TOKENS);
    }

    @Test
    public void pipLegendIsOneMutedLine() {
        assertTrue(PhoneBenchCopy.pipLegend().contains("teal pip"));
        assertTrue(PhoneBenchCopy.pipLegend().contains("this phone"));
    }

    private static final class ScriptedPort implements ModelPort {
        private final ModelResponse first;
        private final ModelResponse second;
        private final Exception failure;
        int generates;

        ScriptedPort(ModelResponse response, Exception failure) {
            this.first = response;
            this.second = null;
            this.failure = failure;
        }

        ScriptedPort(ModelResponse first, ModelResponse second) {
            this.first = first;
            this.second = second;
            this.failure = null;
        }

        @Override
        public void load() {
        }

        @Override
        public ModelResponse generate(ModelRequest request) throws Exception {
            generates++;
            if (failure != null) {
                throw failure;
            }
            return generates == 1 || second == null ? first : second;
        }

        @Override
        public void unload() {
        }

        @Override
        public int tokenCount(String text) {
            return 1;
        }

        @Override
        public String runtimeName() {
            return "scripted";
        }
    }
}
