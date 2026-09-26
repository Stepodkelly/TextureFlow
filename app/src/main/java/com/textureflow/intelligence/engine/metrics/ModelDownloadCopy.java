package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.model.ModelArtifact;

/** Plain-language copy for the Gemma 3 download row. Size is shown before start. */
public final class ModelDownloadCopy {
    private ModelDownloadCopy() {}

    public static long artifactBytes() {
        return ModelArtifact.GEMMA3_1B_IT_INT4.sizeBytes();
    }

    public static String sizeLabel() {
        return sizeLabel(artifactBytes());
    }

    public static String sizeLabel(long bytes) {
        return "~" + toMb(bytes) + " MB";
    }

    public static String idlePrompt() {
        return "Download the on-device model (" + sizeLabel()
                + ", Wi-Fi only). SHA-256 is checked when the file finishes.";
    }

    public static String wifiRequired() {
        return "Connect to Wi-Fi to download. The model is " + sizeLabel()
                + " and will not start on mobile data.";
    }

    public static String ready() {
        return "Model ready on this phone (" + sizeLabel() + ", SHA-256 verified).";
    }

    public static String downloading() {
        return "Downloading…";
    }

    public static String progress(long written, long total) {
        long known = total > 0 ? total : artifactBytes();
        return "Downloading " + toMb(written) + " / " + toMb(known) + " MB…";
    }

    public static String failed(String message) {
        if (message == null || message.isEmpty()) {
            return "Download failed. Stay on Wi-Fi and try again.";
        }
        return message;
    }

    public static String buttonIdle() {
        return "Download model (" + sizeLabel() + ")";
    }

    public static String buttonWifiBlocked() {
        return "Download model (Wi-Fi required)";
    }

    public static String buttonReady() {
        return "Model downloaded";
    }

    static long toMb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }
}
