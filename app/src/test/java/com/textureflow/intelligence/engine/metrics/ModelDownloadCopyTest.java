package com.textureflow.intelligence.engine.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.model.ModelArtifact;

import org.junit.Test;

public final class ModelDownloadCopyTest {
    @Test
    public void sizeIsAbout529MbBeforeStart() {
        assertEquals(554_661_243L, ModelDownloadCopy.artifactBytes());
        assertEquals(554_661_243L, ModelArtifact.GEMMA3_1B_IT_INT4.sizeBytes());
        assertEquals("~529 MB", ModelDownloadCopy.sizeLabel());
        assertTrue(ModelDownloadCopy.idlePrompt().contains("~529 MB"));
        assertTrue(ModelDownloadCopy.buttonIdle().contains("~529 MB"));
    }

    @Test
    public void wifiCopyExplainsTheGate() {
        String copy = ModelDownloadCopy.wifiRequired();
        assertTrue(copy.contains("Wi-Fi"));
        assertTrue(copy.contains("~529 MB"));
        assertTrue(copy.toLowerCase().contains("mobile data"));
    }

    @Test
    public void progressUsesMegabytes() {
        assertEquals(
                "Downloading 100 / 529 MB…",
                ModelDownloadCopy.progress(100L * 1024 * 1024, ModelDownloadCopy.artifactBytes()));
    }

    @Test
    public void readyMentionsShaVerification() {
        assertTrue(ModelDownloadCopy.ready().contains("SHA-256"));
    }
}
