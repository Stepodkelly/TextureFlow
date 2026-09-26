package com.textureflow.intelligence.model;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads versioned role prompts from {@code assets/intelligence/prompts/}. */
public final class PromptAssets {
    public static final String TRIAGE_V1 = "triage.v1.txt";

    private PromptAssets() {}

    public static String loadTriage(Context context) throws IOException {
        return load(context, TRIAGE_V1);
    }

    public static String load(Context context, String fileName) throws IOException {
        Objects.requireNonNull(context, "context");
        String path = "intelligence/prompts/" + fileName;
        try (InputStream in = context.getAssets().open(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
