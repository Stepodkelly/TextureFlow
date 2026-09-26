package com.textureflow.intelligence.model;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;
import java.util.Objects;

/**
 * Production on-device port (debug source set): MediaPipe LLM Inference + Gemma 3 1B int4.
 * The .task is not in the APK. Prefer {@code filesDir/models/}, else adb-pushed
 * {@link #DEFAULT_MODEL_PATH}.
 */
public final class MediaPipeModelPort implements ModelPort {
    public static final String DEFAULT_MODEL_PATH = ModelArtifact.DEV_FALLBACK_PATH;

    private final Context context;
    private final File modelFile;
    private final NoModelPort tokens = new NoModelPort();
    private LlmInference inference;

    public MediaPipeModelPort(Context context, File modelFile) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.modelFile = Objects.requireNonNull(modelFile, "modelFile");
    }

    public static MediaPipeModelPort fromDefaultPath(Context context) {
        return new MediaPipeModelPort(context, ModelFiles.resolve(context));
    }

    public static MediaPipeModelPort create(Context context) {
        return fromDefaultPath(context);
    }

    @Override
    public void load() throws Exception {
        if (!modelFile.isFile() || modelFile.length() < 10_000_000L) {
            throw new ModelUnavailableException(
                    "MediaPipe model missing or too small at " + modelFile.getAbsolutePath());
        }
        unload();
        LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.getAbsolutePath())
                .setMaxTokens(1024)
                .setMaxTopK(64)
                .build();
        inference = LlmInference.createFromOptions(context, options);
    }

    @Override
    public ModelResponse generate(ModelRequest request) throws Exception {
        if (inference == null) {
            throw new IllegalStateException("load() first");
        }
        int inputTokens = tokenCount(request.getPrompt());
        long started = System.nanoTime();
        String text = inference.generateResponse(request.getPrompt());
        long wallMs = (System.nanoTime() - started) / 1_000_000L;
        String out = text == null ? "" : text;
        return new ModelResponse(out, inputTokens, tokenCount(out), wallMs);
    }

    @Override
    public void unload() {
        if (inference != null) {
            inference.close();
            inference = null;
        }
    }

    @Override
    public int tokenCount(String text) {
        return tokens.tokenCount(text);
    }

    @Override
    public String runtimeName() {
        return "mediapipe_tasks_genai";
    }

    public long modelBytes() {
        return modelFile.isFile() ? modelFile.length() : 0L;
    }
}
