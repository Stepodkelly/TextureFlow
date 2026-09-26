package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ModelFilesTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resolvePrefersLocalModelsDir() throws Exception {
        File local = ModelFiles.localFile(folder.getRoot(), ModelArtifact.GEMMA3_1B_IT_INT4);
        assertTrue(local.getParentFile().mkdirs() || local.getParentFile().isDirectory());
        byte[] bytes = new byte[(int) ModelFiles.MIN_USABLE_BYTES];
        Files.write(local.toPath(), bytes);
        assertEquals(local.getAbsolutePath(), ModelFiles.resolve(folder.getRoot()).getAbsolutePath());
        assertTrue(ModelFiles.isPresent(local));
    }

    @Test
    public void resolveReturnsLocalDestinationWhenNothingPresent() {
        File resolved = ModelFiles.resolve(folder.getRoot());
        assertEquals(
                ModelFiles.localFile(folder.getRoot(), ModelArtifact.GEMMA3_1B_IT_INT4).getAbsolutePath(),
                resolved.getAbsolutePath());
        assertFalse(ModelFiles.isPresent(resolved));
    }

    @Test
    public void sha256MatchesKnownVector() throws Exception {
        File file = folder.newFile("abc.bin");
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ModelFiles.sha256Hex(file));
    }
}
