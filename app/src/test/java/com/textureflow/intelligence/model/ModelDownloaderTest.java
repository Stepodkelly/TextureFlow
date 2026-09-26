package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ModelDownloaderTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void rejectsWhenNotOnWifi() throws Exception {
        byte[] payload = "tiny-model".getBytes(StandardCharsets.UTF_8);
        ModelArtifact artifact = artifact(payload);
        ScriptedHttp http = new ScriptedHttp();
        ModelDownloader downloader = new ModelDownloader(
                artifact, folder.getRoot(), http, NetworkPolicy.NEVER);
        try {
            downloader.download();
            fail("expected Wi-Fi gate");
        } catch (ModelDownloadException e) {
            assertTrue(e.getMessage().contains("Wi-Fi"));
        }
        assertTrue(http.starts.isEmpty());
    }

    @Test
    public void resumesAndVerifiesSha256() throws Exception {
        byte[] payload = "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8);
        ModelArtifact artifact = artifact(payload);
        ScriptedHttp http = new ScriptedHttp();
        http.enqueue(Arrays.copyOfRange(payload, 0, 8), false);
        http.enqueue(Arrays.copyOfRange(payload, 8, payload.length), true);

        ModelDownloader downloader = new ModelDownloader(
                artifact, folder.getRoot(), http, NetworkPolicy.ALWAYS);
        try {
            downloader.download();
            fail("first partial should fail size check");
        } catch (ModelDownloadException e) {
            assertTrue(e.getMessage().contains("size"));
        }

        File published = downloader.download();
        assertTrue(ModelFiles.matchesArtifact(published, artifact));
        assertEquals(Arrays.asList(0L, 8L), http.starts);
        assertEquals(new String(payload, StandardCharsets.UTF_8),
                new String(Files.readAllBytes(published.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void skipsDownloadWhenHashAlreadyMatches() throws Exception {
        byte[] payload = "already-there".getBytes(StandardCharsets.UTF_8);
        ModelArtifact artifact = artifact(payload);
        File target = ModelFiles.localFile(folder.getRoot(), artifact);
        assertTrue(target.getParentFile().mkdirs() || target.getParentFile().isDirectory());
        Files.write(target.toPath(), payload);

        ScriptedHttp http = new ScriptedHttp();
        ModelDownloader downloader = new ModelDownloader(
                artifact, folder.getRoot(), http, NetworkPolicy.ALWAYS);
        assertEquals(target.getAbsolutePath(), downloader.download().getAbsolutePath());
        assertTrue(http.starts.isEmpty());
    }

    @Test
    public void deletesPartOnHashMismatch() throws Exception {
        byte[] expected = "expected-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] actual = "tampered-bytes".getBytes(StandardCharsets.UTF_8);
        ModelArtifact artifact = artifact(expected);
        ScriptedHttp http = new ScriptedHttp();
        http.enqueue(actual, false);
        ModelDownloader downloader = new ModelDownloader(
                artifact, folder.getRoot(), http, NetworkPolicy.ALWAYS);
        try {
            downloader.download();
            fail("hash must fail");
        } catch (ModelDownloadException e) {
            assertTrue(e.getMessage().contains("SHA-256"));
        }
        assertTrue(!ModelFiles.partFile(downloader.targetFile()).exists());
    }

    private static ModelArtifact artifact(byte[] payload) throws IOException {
        File tmp = File.createTempFile("hash", ".bin");
        Files.write(tmp.toPath(), payload);
        String hash = ModelFiles.sha256Hex(tmp);
        tmp.delete();
        return new ModelArtifact("tiny.task", "https://example.test/tiny.task", hash, payload.length);
    }

    private static final class ScriptedHttp implements HttpRangeClient {
        private final List<ScriptedResponse> queue = new ArrayList<>();
        final List<Long> starts = new ArrayList<>();

        void enqueue(byte[] body, boolean partial) {
            queue.add(new ScriptedResponse(body, partial));
        }

        @Override
        public HttpRangeResponse open(String url, long startByte) {
            starts.add(startByte);
            if (queue.isEmpty()) {
                throw new IllegalStateException("no scripted response for " + startByte);
            }
            ScriptedResponse next = queue.remove(0);
            return new HttpRangeResponse(
                    new ByteArrayInputStream(next.body),
                    next.partial,
                    next.body.length);
        }
    }

    private static final class ScriptedResponse {
        final byte[] body;
        final boolean partial;

        ScriptedResponse(byte[] body, boolean partial) {
            this.body = body;
            this.partial = partial;
        }
    }
}
