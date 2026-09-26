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
    public void shortGenerateReportsWallTime() {
        String text = PhoneBenchRunner.run(true, CapabilityTier.T3, new ScriptedPort(
                new ModelResponse("ok", 4, 1, 42), null));
        assertEquals("Fast: this phone can run roles in parallel. Short generate finished in 42 ms.", text);
    }

    @Test
    public void generateFailureBecomesCopy() {
        String text = PhoneBenchRunner.run(true, CapabilityTier.T0, new ScriptedPort(
                null, new IllegalStateException("No on-device model is loaded.")));
        assertTrue(text.contains("could not run"));
        assertTrue(text.contains("No on-device model is loaded."));
        assertTrue(text.contains("Ranking still works"));
    }

    @Test
    public void pipLegendIsOneMutedLine() {
        assertTrue(PhoneBenchCopy.pipLegend().contains("teal pip"));
        assertTrue(PhoneBenchCopy.pipLegend().contains("this phone"));
    }

    private static final class ScriptedPort implements ModelPort {
        private final ModelResponse response;
        private final Exception failure;

        ScriptedPort(ModelResponse response, Exception failure) {
            this.response = response;
            this.failure = failure;
        }

        @Override
        public void load() {
        }

        @Override
        public ModelResponse generate(ModelRequest request) throws Exception {
            if (failure != null) {
                throw failure;
            }
            return response;
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
