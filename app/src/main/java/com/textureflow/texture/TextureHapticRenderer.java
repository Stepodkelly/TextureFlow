package com.textureflow.texture;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

final class TextureHapticRenderer {
    private final Vibrator vibrator;

    TextureHapticRenderer(Context context) {
        vibrator = context.getSystemService(Vibrator.class);
    }

    boolean play(TextureCue cue, float strength, View fallbackView) {
        if (strength <= 0f) {
            return false;
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                vibrator.cancel();
                vibrator.vibrate(effectFor(cue, strength));
                return true;
            } catch (RuntimeException ignored) {
                // Fall through to semantic view feedback when vibration is unavailable.
            }
        }
        return fallbackView != null && fallbackView.performHapticFeedback(fallbackFor(cue));
    }

    void stop() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (RuntimeException ignored) {
                // Some vendor implementations invalidate the vibrator during teardown.
            }
        }
    }

    private VibrationEffect effectFor(TextureCue cue, float strength) {
        switch (cue) {
            case CONTENT_MOVEMENT:
                return oneShot(7, 32, strength);
            case FOCUS_ENTERED:
                return oneShot(14, 74, strength);
            case LISTENING_STARTED:
                return oneShot(24, 54, strength);
            case ATTENTION_URGENT:
                return waveform(new long[]{0, 28, 55, 38}, new int[]{0, 116, 0, 154}, strength);
            case PROPOSAL_READY:
                return waveform(new long[]{0, 18, 72, 24}, new int[]{0, 74, 0, 92}, strength);
            case CONFIRMATION_REQUIRED:
                return waveform(new long[]{0, 38, 110, 48}, new int[]{0, 116, 0, 136}, strength);
            case EXECUTION_STARTED:
                return waveform(new long[]{0, 18, 22, 22, 24, 28}, new int[]{0, 48, 0, 88, 0, 132}, strength);
            case ACTION_DISPATCHED:
                return waveform(new long[]{0, 42, 58, 26}, new int[]{0, 188, 0, 112}, strength);
            case ACTION_FAILED:
                return waveform(new long[]{0, 42, 38, 48, 34, 62}, new int[]{0, 152, 0, 196, 0, 124}, strength);
            case CANCELLED:
            default:
                return waveform(new long[]{0, 34, 45, 18}, new int[]{0, 112, 0, 58}, strength);
        }
    }

    private VibrationEffect oneShot(long durationMs, int amplitude, float strength) {
        return VibrationEffect.createOneShot(durationMs, scaled(amplitude, strength));
    }

    private VibrationEffect waveform(long[] timings, int[] amplitudes, float strength) {
        int[] scaled = new int[amplitudes.length];
        boolean amplitudeControl = vibrator != null && vibrator.hasAmplitudeControl();
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] == 0) {
                scaled[i] = 0;
            } else if (amplitudeControl) {
                scaled[i] = scaled(amplitudes[i], strength);
            } else {
                scaled[i] = VibrationEffect.DEFAULT_AMPLITUDE;
            }
        }
        return VibrationEffect.createWaveform(timings, scaled, -1);
    }

    private static int scaled(int amplitude, float strength) {
        return Math.max(1, Math.min(255, Math.round(amplitude * strength)));
    }

    private static int fallbackFor(TextureCue cue) {
        if (cue == TextureCue.CONTENT_MOVEMENT) {
            return HapticFeedbackConstants.TEXT_HANDLE_MOVE;
        }
        if (cue == TextureCue.ACTION_FAILED || cue == TextureCue.ATTENTION_URGENT) {
            return HapticFeedbackConstants.LONG_PRESS;
        }
        if (cue == TextureCue.ACTION_DISPATCHED || cue == TextureCue.CONFIRMATION_REQUIRED) {
            return HapticFeedbackConstants.CONFIRM;
        }
        return HapticFeedbackConstants.VIRTUAL_KEY;
    }
}
