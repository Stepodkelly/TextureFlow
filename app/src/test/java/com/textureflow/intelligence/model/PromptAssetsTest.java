package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PromptAssetsTest {
    @Test
    public void versionedPromptFileNames() {
        assertEquals("triage.v1.txt", PromptAssets.TRIAGE_V1);
        assertEquals("summary.v1.txt", PromptAssets.SUMMARY_V1);
        assertEquals("draft.v1.txt", PromptAssets.DRAFT_V1);
    }
}
