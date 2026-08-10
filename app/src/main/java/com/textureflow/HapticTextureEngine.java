package com.textureflow;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.textureflow.texture.SensoryProfile;
import com.textureflow.texture.TextureCue;
import com.textureflow.texture.TextureCueScheduler;

/** Backwards-compatible UI facade over the semantic Texture Engine. */
public final class HapticTextureEngine {
    public interface ScrollObserver {
        void onScroll(int scrollY);
    }

    private static final long MOVEMENT_STOP_DELAY_MS = 118L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextureCueScheduler scheduler;
    private final Runnable stopMovement;

    public HapticTextureEngine(Context context) {
        scheduler = new TextureCueScheduler(context);
        stopMovement = scheduler::cancelMovement;
    }

    public void setListener(TextureCueScheduler.Listener listener) {
        scheduler.setListener(listener);
    }

    public void setEnabled(boolean enabled) {
        scheduler.setAudioEnabled(enabled);
        scheduler.setHapticsEnabled(enabled);
    }

    public boolean isEnabled() {
        return scheduler.isAudioEnabled() || scheduler.isHapticsEnabled();
    }

    public void setAudioEnabled(boolean enabled) {
        scheduler.setAudioEnabled(enabled);
    }

    public boolean isAudioEnabled() {
        return scheduler.isAudioEnabled();
    }

    public void setHapticsEnabled(boolean enabled) {
        scheduler.setHapticsEnabled(enabled);
    }

    public boolean isHapticsEnabled() {
        return scheduler.isHapticsEnabled();
    }

    public void setProfile(SensoryProfile profile) {
        scheduler.setProfile(profile);
    }

    public SensoryProfile getProfile() {
        return scheduler.getProfile();
    }

    public void setSpeechActive(boolean active) {
        scheduler.setSpeechActive(active);
    }

    public void setForeground(boolean foreground) {
        scheduler.setForeground(foreground);
    }

    public void emit(TextureCue cue, String correlationId, View fallbackView) {
        scheduler.schedule(cue, correlationId, fallbackView);
    }

    public void cancel(String correlationId) {
        scheduler.cancel(correlationId);
    }

    public void attachScrollTexture(View view) {
        attachScrollTexture(view, null);
    }

    public void attachScrollTexture(View view, ScrollObserver observer) {
        view.setOnScrollChangeListener((source, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY == oldScrollY && scrollX == oldScrollX) {
                return;
            }
            if (observer != null) {
                observer.onScroll(scrollY);
            }
            scheduler.schedule(TextureCue.CONTENT_MOVEMENT, "scroll", source);
            mainHandler.removeCallbacks(stopMovement);
            mainHandler.postDelayed(stopMovement, MOVEMENT_STOP_DELAY_MS);
        });
    }

    public void attachGlassControl(View view) {
        view.setOnFocusChangeListener((source, hasFocus) -> {
            if (hasFocus) {
                scheduler.schedule(TextureCue.FOCUS_ENTERED, focusCorrelation(source), source);
            }
        });
        view.setOnHoverListener((source, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                scheduler.schedule(TextureCue.FOCUS_ENTERED, focusCorrelation(source), source);
            }
            return false;
        });
        view.setOnTouchListener((source, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                scheduler.schedule(TextureCue.FOCUS_ENTERED, focusCorrelation(source), source);
            }
            return false;
        });
    }

    public void playCarpetScroll(View source) {
        scheduler.schedule(TextureCue.CONTENT_MOVEMENT, "scroll", source);
    }

    public void playGlassTouch(View source) {
        scheduler.schedule(TextureCue.FOCUS_ENTERED, focusCorrelation(source), source);
    }

    public void playBoundaryBump(View source) {
        scheduler.schedule(TextureCue.FOCUS_ENTERED, focusCorrelation(source), source);
    }

    public void release() {
        mainHandler.removeCallbacks(stopMovement);
        scheduler.release();
    }

    private static String focusCorrelation(View view) {
        return "focus-" + System.identityHashCode(view) + '-' + SystemClock.uptimeMillis();
    }
}
