package com.textureflow.intelligence.model;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * On-demand Gemma 3 1B int4 download: Wi‑Fi only, resumable, SHA-256 verified,
 * stored under {@code filesDir/models/}. Never writes into the APK.
 */
public final class ModelDownloader {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final ModelArtifact artifact;
    private final File target;
    private final HttpRangeClient http;
    private final NetworkPolicy network;

    public ModelDownloader(
            ModelArtifact artifact,
            File filesDir,
            HttpRangeClient http,
            NetworkPolicy network) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.target = ModelFiles.localFile(Objects.requireNonNull(filesDir, "filesDir"), artifact);
        this.http = Objects.requireNonNull(http, "http");
        this.network = Objects.requireNonNull(network, "network");
    }

    public static ModelDownloader gemma3(Context context) {
        return new ModelDownloader(
                ModelArtifact.GEMMA3_1B_IT_INT4,
                context.getFilesDir(),
                new UrlConnectionRangeClient(),
                new AndroidNetworkPolicy(context));
    }

    public File targetFile() {
        return target;
    }

    public boolean isComplete() {
        try {
            return ModelFiles.matchesArtifact(target, artifact);
        } catch (IOException e) {
            return false;
        }
    }

    public File download() throws ModelDownloadException {
        return download(null);
    }

    public File download(DownloadListener listener) throws ModelDownloadException {
        if (isComplete()) {
            notify(listener, artifact.sizeBytes(), artifact.sizeBytes());
            return target;
        }
        if (!network.allowDownload()) {
            throw new ModelDownloadException("Model download requires Wi-Fi.");
        }
        File dir = target.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new ModelDownloadException("Could not create " + dir.getAbsolutePath());
        }
        File part = ModelFiles.partFile(target);
        try {
            writePart(part, listener);
            verifyPart(part);
            if (target.exists() && !target.delete()) {
                throw new ModelDownloadException("Could not replace " + target.getAbsolutePath());
            }
            if (!part.renameTo(target)) {
                throw new ModelDownloadException("Could not publish " + target.getAbsolutePath());
            }
            return target;
        } catch (ModelDownloadException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelDownloadException("Model download failed.", e);
        }
    }

    private void writePart(File part, DownloadListener listener) throws IOException, ModelDownloadException {
        long existing = part.isFile() ? part.length() : 0L;
        if (existing > artifact.sizeBytes()) {
            if (!part.delete()) {
                throw new ModelDownloadException("Corrupt partial download could not be removed.");
            }
            existing = 0L;
        }
        try (HttpRangeResponse response = http.open(artifact.url(), existing)) {
            long start = existing;
            if (start > 0 && !response.partial()) {
                start = 0L;
            }
            boolean append = start > 0;
            try (InputStream in = response.body();
                    FileOutputStream out = new FileOutputStream(part, append)) {
                if (!append) {
                    existing = 0L;
                }
                byte[] buffer = new byte[BUFFER_BYTES];
                long written = existing;
                notify(listener, written, artifact.sizeBytes());
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    out.write(buffer, 0, read);
                    written += read;
                    notify(listener, written, artifact.sizeBytes());
                }
                out.getFD().sync();
            }
        }
    }

    private void verifyPart(File part) throws IOException, ModelDownloadException {
        if (artifact.sizeBytes() > 0 && part.length() != artifact.sizeBytes()) {
            throw new ModelDownloadException(
                    "Downloaded size " + part.length() + " != " + artifact.sizeBytes());
        }
        String actual = ModelFiles.sha256Hex(part);
        if (!actual.equals(artifact.sha256Hex())) {
            if (!part.delete()) {
                // still fail closed on hash
            }
            throw new ModelDownloadException("SHA-256 mismatch for " + artifact.fileName());
        }
    }

    private static void notify(DownloadListener listener, long written, long total) {
        if (listener != null) {
            listener.onProgress(written, total);
        }
    }
}
