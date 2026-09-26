package com.textureflow.intelligence.engine;

import org.junit.Test;

import java.io.IOException;

public final class NoAndroidImportsTest {
    @Test
    public void engineSourcesHaveNoAndroidImports() throws IOException {
        com.textureflow.intelligence.shield.NoAndroidImportsTest.assertNoAndroidImports(
                com.textureflow.intelligence.shield.NoAndroidImportsTest.sourceDir("engine"));
    }

    @Test
    public void jobSourcesHaveNoAndroidImports() throws IOException {
        com.textureflow.intelligence.shield.NoAndroidImportsTest.assertNoAndroidImports(
                com.textureflow.intelligence.shield.NoAndroidImportsTest.sourceDir("jobs"));
    }
}
