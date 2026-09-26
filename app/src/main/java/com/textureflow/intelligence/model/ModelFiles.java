package com.textureflow.intelligence.model;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * App-private model path plus the adb-pushed Stream E fallback.
 * Presence never requires the 529 MB file in the APK.
 */
public final class ModelFiles {
    public static final long MIN_USABLE_BYTES = 10_000_000L;

    private ModelFiles() {}

    public static File modelsDir(File filesDir) {
        return new File(Objects.requireNonNull(filesDir, "filesDir"), "models");
    }

    public static File modelsDir(Context context) {
        return modelsDir(Objects.requireNonNull(context, "context").getFilesDir());
    }

    public static File localFile(File filesDir, ModelArtifact artifact) {
        return new File(modelsDir(filesDir), artifact.fileName());
    }

    public static File localFile(Context context) {
        return localFile(context.getFilesDir(), ModelArtifact.GEMMA3_1B_IT_INT4);
    }

    public static File partFile(File target) {
        return new File(target.getParentFile(), target.getName() + ".part");
    }

    /**
     * Prefer a verified (or large enough) file in {@code filesDir/models/}.
     * Fall back to {@link ModelArtifact#DEV_FALLBACK_PATH} for local benches.
     */
    public static File resolve(File filesDir) {
        File local = localFile(filesDir, ModelArtifact.GEMMA3_1B_IT_INT4);
        if (isPresent(local)) {
            return local;
        }
        File pushed = new File(ModelArtifact.DEV_FALLBACK_PATH);
        if (isPresent(pushed)) {
            return pushed;
        }
        return local;
    }

    public static File resolve(Context context) {
        return resolve(context.getFilesDir());
    }

    public static boolean isPresent(File file) {
        return file != null && file.isFile() && file.length() >= MIN_USABLE_BYTES;
    }

    public static boolean matchesArtifact(File file, ModelArtifact artifact) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        if (artifact.sizeBytes() > 0 && file.length() != artifact.sizeBytes()) {
            return false;
        }
        return sha256Hex(file).equals(artifact.sha256Hex());
    }

    public static String sha256Hex(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is required", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    public static String toHex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.US, "%02x", value));
        }
        return hex.toString();
    }
}
