package com.textureflow.texture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arbitrates semantic cues. Domain state chooses the cue; this class chooses
 * which enabled sensory channels may render it and when it must be cancelled.
 */
public final class TextureCueScheduler {
    public interface Listener {
        void onCueStarted(TextureCue cue, String correlationId);

        void onCueFinished(TextureCue cue, String correlationId, boolean cancelled);
    }

    private static final long MOVEMENT_RATE_LIMIT_MS = 72L;
    private static final long FOCUS_RATE_LIMIT_MS = 190L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextureAudioRenderer audioRenderer;
    private final TextureHapticRenderer hapticRenderer;
    private final Map<TextureCue, Long> lastRenderedAt = new EnumMap<>(TextureCue.class);
    private final LinkedHashMap<String, Boolean> renderedCorrelations =
            new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 128;
                }
            };

    private SensoryProfile profile = SensoryProfile.BALANCED;
    private Listener listener;
    private TextureCue activeCue;
    private String activeCorrelationId;
    private long activeUntil;
    private int activeGeneration;
    private boolean audioEnabled = true;
    private boolean hapticsEnabled = true;
    private boolean speechActive;
    private boolean foreground = true;

    public TextureCueScheduler(Context context) {
        Context appContext = context.getApplicationContext();
        audioRenderer = new TextureAudioRenderer(appContext);
        hapticRenderer = new TextureHapticRenderer(appContext);
    }

    public void setListener(Listener listener) {
        runOnMain(() -> this.listener = listener);
    }

    public void setProfile(SensoryProfile profile) {
        if (profile == null) {
            return;
        }
        runOnMain(() -> {
            this.profile = profile;
            if (profile == SensoryProfile.VISUAL_ONLY) {
                audioRenderer.stop();
                hapticRenderer.stop();
            }
        });
    }

    public SensoryProfile getProfile() {
        return profile;
    }

    public void setAudioEnabled(boolean enabled) {
        runOnMain(() -> {
            audioEnabled = enabled;
            if (!enabled) {
                audioRenderer.stop();
            }
        });
    }

    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    public void setHapticsEnabled(boolean enabled) {
        runOnMain(() -> {
            hapticsEnabled = enabled;
            if (!enabled) {
                hapticRenderer.stop();
            }
        });
    }

    public boolean isHapticsEnabled() {
        return hapticsEnabled;
    }

    public void setSpeechActive(boolean active) {
        runOnMain(() -> {
            speechActive = active;
            if (active) {
                audioRenderer.stop();
                cancelMovementInternal();
            }
        });
    }

    public void setForeground(boolean foreground) {
        runOnMain(() -> {
            this.foreground = foreground;
            if (!foreground) {
                cancelAllInternal();
            }
        });
    }

    public void schedule(TextureCue cue, String correlationId, View hapticFallbackView) {
        if (cue == null) {
            return;
        }
        runOnMain(() -> scheduleInternal(cue, normalizeCorrelation(cue, correlationId), hapticFallbackView));
    }

    public void cancel(String correlationId) {
        if (correlationId == null) {
            return;
        }
        runOnMain(() -> {
            if (correlationId.equals(activeCorrelationId)) {
                cancelActiveInternal();
            }
        });
    }

    public void cancelMovement() {
        runOnMain(this::cancelMovementInternal);
    }

    public void cancelAll() {
        runOnMain(this::cancelAllInternal);
    }

    public void release() {
        runOnMain(() -> {
            cancelAllInternal();
            audioRenderer.release();
            listener = null;
        });
    }

    private void scheduleInternal(TextureCue cue, String correlationId, View hapticFallbackView) {
        if (!foreground || shouldRateLimit(cue) || wasRendered(cue, correlationId)) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        boolean hasActiveCue = activeCue != null && activeUntil > now;
        if (hasActiveCue && activeCue.priority().ordinal() > cue.priority().ordinal()) {
            return;
        }
        if (hasActiveCue) {
            cancelActiveInternal();
        }

        markRendered(cue, correlationId, now);
        activeCue = cue;
        activeCorrelationId = correlationId;
        activeUntil = now + cue.durationMs();
        int generation = ++activeGeneration;

        if (listener != null) {
            listener.onCueStarted(cue, correlationId);
        }

        boolean suppressAudioForSpeech = speechActive
                && cue.speechPolicy() == TextureCue.SpeechPolicy.SUPPRESS_UNDER_SPEECH;
        boolean duckAudioForSpeech = speechActive
                && cue.speechPolicy() == TextureCue.SpeechPolicy.DUCK_UNDER_SPEECH;
        if (audioEnabled && !suppressAudioForSpeech && profile.allowsAudio(cue)) {
            float strength = profile.audioStrength() * (duckAudioForSpeech ? 0.24f : 1f);
            // A ducked cue may mix quietly, but never requests focus away from speech.
            audioRenderer.play(cue, strength, !speechActive);
        }
        if (hapticsEnabled && profile.allowsHaptics(cue)) {
            hapticRenderer.play(cue, profile.hapticStrength(), hapticFallbackView);
        }

        mainHandler.postDelayed(() -> finishIfCurrent(cue, correlationId, generation), cue.durationMs());
    }

    private boolean shouldRateLimit(TextureCue cue) {
        if (cue.repeatPolicy() != TextureCue.RepeatPolicy.RATE_LIMITED) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        Long last = lastRenderedAt.get(cue);
        long limit = cue == TextureCue.CONTENT_MOVEMENT ? MOVEMENT_RATE_LIMIT_MS : FOCUS_RATE_LIMIT_MS;
        return last != null && now - last < limit;
    }

    private boolean wasRendered(TextureCue cue, String correlationId) {
        return cue.repeatPolicy() == TextureCue.RepeatPolicy.ONCE_PER_CORRELATION
                && renderedCorrelations.containsKey(cue.name() + ':' + correlationId);
    }

    private void markRendered(TextureCue cue, String correlationId, long now) {
        lastRenderedAt.put(cue, now);
        if (cue.repeatPolicy() == TextureCue.RepeatPolicy.ONCE_PER_CORRELATION) {
            renderedCorrelations.put(cue.name() + ':' + correlationId, Boolean.TRUE);
        }
    }

    private void finishIfCurrent(TextureCue cue, String correlationId, int generation) {
        if (generation != activeGeneration || cue != activeCue || !correlationId.equals(activeCorrelationId)) {
            return;
        }
        Listener currentListener = listener;
        clearActive();
        if (currentListener != null) {
            currentListener.onCueFinished(cue, correlationId, false);
        }
    }

    private void cancelMovementInternal() {
        if (activeCue == TextureCue.CONTENT_MOVEMENT) {
            cancelActiveInternal();
        }
    }

    private void cancelAllInternal() {
        cancelActiveInternal();
        audioRenderer.stop();
        hapticRenderer.stop();
    }

    private void cancelActiveInternal() {
        TextureCue cancelledCue = activeCue;
        String cancelledCorrelation = activeCorrelationId;
        ++activeGeneration;
        clearActive();
        audioRenderer.stop();
        hapticRenderer.stop();
        if (cancelledCue != null && listener != null) {
            listener.onCueFinished(cancelledCue, cancelledCorrelation, true);
        }
    }

    private void clearActive() {
        activeCue = null;
        activeCorrelationId = null;
        activeUntil = 0L;
    }

    private String normalizeCorrelation(TextureCue cue, String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return cue.name() + '-' + SystemClock.uptimeMillis();
        }
        return correlationId;
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
