package com.textureflow.intelligence.roles;

import org.junit.Test;

import java.io.IOException;

public final class NoAndroidImportsTest {
    @Test
    public void roleSourcesHaveNoAndroidImports() throws IOException {
        com.textureflow.intelligence.shield.NoAndroidImportsTest.assertNoAndroidImports(
                com.textureflow.intelligence.shield.NoAndroidImportsTest.sourceDir("roles"));
    }
}
