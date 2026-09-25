package com.textureflow.intelligence.policy;

import org.junit.Test;

import java.io.IOException;

public final class NoAndroidImportsTest {
    @Test
    public void policySourcesHaveNoAndroidImports() throws IOException {
        com.textureflow.intelligence.shield.NoAndroidImportsTest.assertNoAndroidImports(
                com.textureflow.intelligence.shield.NoAndroidImportsTest.sourceDir("policy"));
    }
}
