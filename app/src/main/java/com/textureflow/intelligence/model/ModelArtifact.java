package com.textureflow.intelligence.model;

import java.util.Objects;

/** One on-device model file. The 529 MB .task is never packaged in the APK. */
public final class ModelArtifact {
    public static final ModelArtifact GEMMA3_1B_IT_INT4 = new ModelArtifact(
            "gemma3-1b-it-int4.task",
            "https://huggingface.co/nikhil2024/gemma3-1b-it-litert-mirror/resolve/main/gemma3-1b-it-int4.task",
            "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee",
            554_661_243L);

    /** adb-pushed path used by Stream E benches. Dev fallback when filesDir has no file. */
    public static final String DEV_FALLBACK_PATH = "/data/local/tmp/llm/gemma3-1b-it-int4.task";

    private final String fileName;
    private final String url;
    private final String sha256Hex;
    private final long sizeBytes;

    public ModelArtifact(String fileName, String url, String sha256Hex, long sizeBytes) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.url = Objects.requireNonNull(url, "url");
        this.sha256Hex = Objects.requireNonNull(sha256Hex, "sha256Hex").toLowerCase();
        this.sizeBytes = sizeBytes;
    }

    public String fileName() {
        return fileName;
    }

    public String url() {
        return url;
    }

    public String sha256Hex() {
        return sha256Hex;
    }

    public long sizeBytes() {
        return sizeBytes;
    }
}
