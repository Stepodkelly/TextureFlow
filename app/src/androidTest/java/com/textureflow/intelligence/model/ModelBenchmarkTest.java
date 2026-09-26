package com.textureflow.intelligence.model;

import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * On-device Stream E bench: 600-token prefill + 64-token decode when a model is present.
 * Always records device facts even if inference is skipped.
 */
@RunWith(AndroidJUnit4.class)
public final class ModelBenchmarkTest {
    private static final String TAG = "TextureFlowBench";
    private static final int PREFILL_TOKENS = 600;
    private static final int DECODE_TOKENS = 64;

    @Test
    public void benchmarkOnDeviceRuntimes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StringBuilder report = new StringBuilder();
        report.append("# TextureFlow model bench\n\n");
        appendDevice(report, context);

        boolean aicore = PlatformModelProbe.aicorePresent(context);
        report.append("## Runtime A — Gemini Nano / AICore\n\n");
        report.append("- present: ").append(aicore).append('\n');
        report.append("- used: no (not on this Xiaomi; Pixel-class only)\n\n");

        report.append("## Runtime C — llama.cpp JNI\n\n");
        report.append("- used: no (JNI spike deferred; MediaPipe is the Android-first path)\n\n");

        report.append("## Runtime B — MediaPipe tasks-genai + Gemma 3 1B int4\n\n");
        File model = new File(MediaPipeModelPort.DEFAULT_MODEL_PATH);
        report.append("- model path: ").append(model.getAbsolutePath()).append('\n');
        report.append("- model present: ").append(model.isFile()).append('\n');
        report.append("- model bytes: ").append(model.isFile() ? model.length() : 0).append('\n');

        if (!model.isFile() || model.length() < 10_000_000L) {
            report.append("- result: SKIPPED (push gemma3-1b-it-int4.task first)\n");
            writeReport(context, report.toString());
            Log.i(TAG, report.toString());
            assertTrue("device snapshot written", true);
            return;
        }

        MediaPipeModelPort port = new MediaPipeModelPort(context, model);
        long beforeMb = availableRamMb(context);
        long loadStart = System.nanoTime();
        try {
            port.load();
        } catch (Exception failure) {
            report.append("- load: FAILED ").append(failure.getMessage()).append('\n');
            writeReport(context, report.toString());
            Log.e(TAG, report.toString(), failure);
            throw failure;
        }
        long loadMs = (System.nanoTime() - loadStart) / 1_000_000L;
        long afterLoadMb = availableRamMb(context);
        report.append("- load_ms: ").append(loadMs).append('\n');
        report.append("- ram_available_mb_before: ").append(beforeMb).append('\n');
        report.append("- ram_available_mb_after_load: ").append(afterLoadMb).append('\n');

        String prompt = paddedTriagePrompt(port, PREFILL_TOKENS);
        int promptTokens = port.tokenCount(prompt);
        report.append("- prompt_tokens_est: ").append(promptTokens).append('\n');

        ModelRequest request = new ModelRequest(prompt, DECODE_TOKENS, 0.2f);
        ModelResponse response = port.generate(request);
        port.unload();

        report.append("- generate_wall_ms: ").append(response.getWallMs()).append('\n');
        report.append("- output_tokens_est: ").append(response.getOutputTokens()).append('\n');
        report.append(String.format(Locale.US, "- decode_tok_s_est: %.2f%n", response.decodeTokPerSec()));
        report.append("- output_preview: ")
                .append(response.getText().replace('\n', ' '), 0,
                        Math.min(180, response.getText().length()))
                .append('\n');

        writeReport(context, report.toString());
        Log.i(TAG, report.toString());
        assertTrue("inference produced text", !response.getText().isEmpty());
    }

    private static void appendDevice(StringBuilder report, Context context) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        manager.getMemoryInfo(memory);
        StatFs data = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        report.append("## Device\n\n");
        report.append("- manufacturer: ").append(Build.MANUFACTURER).append('\n');
        report.append("- model: ").append(Build.MODEL).append('\n');
        report.append("- device: ").append(Build.DEVICE).append('\n');
        report.append("- soc: ").append(Build.SOC_MODEL).append('\n');
        report.append("- sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("- abi: ").append(Build.SUPPORTED_ABIS[0]).append('\n');
        report.append("- ram_total_mb: ").append(memory.totalMem / (1024 * 1024)).append('\n');
        report.append("- ram_available_mb: ").append(memory.availMem / (1024 * 1024)).append('\n');
        report.append("- data_free_mb: ").append(data.getAvailableBytes() / (1024 * 1024)).append('\n');
        report.append('\n');
    }

    private static long availableRamMb(Context context) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        manager.getMemoryInfo(memory);
        return memory.availMem / (1024 * 1024);
    }

    private static String paddedTriagePrompt(ModelPort port, int targetTokens) {
        String header = "You classify one untrusted notification. "
                + "Return JSON only: {\"intent\":\"REQUEST\",\"requiresResponse\":true,"
                + "\"urgency\":\"IMPORTANT\",\"reason\":\"short\",\"modelConfidence\":0.6}.\n"
                + "<untrusted>I'm downstairs, can you come down?</untrusted>\n"
                + "Padding:\n";
        StringBuilder body = new StringBuilder(header);
        String pad = "word ";
        while (port.tokenCount(body.toString()) < targetTokens) {
            body.append(pad);
        }
        return body.toString();
    }

    private static void writeReport(Context context, String text) throws Exception {
        File out = new File(context.getExternalFilesDir(null), "model-bench.md");
        Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Log.i(TAG, "Wrote " + out.getAbsolutePath());
    }
}
