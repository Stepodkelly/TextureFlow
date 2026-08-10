package com.textureflow.texture;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class TextureAudioRenderer {
    private static final int SAMPLE_RATE = 24000;

    private final AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicReference<AudioTrack> activeTrack = new AtomicReference<>();
    private final Map<TextureCue, short[]> samples = new EnumMap<>(TextureCue.class);
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private boolean hasAudioFocus;

    TextureAudioRenderer(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::onAudioFocusChanged, mainHandler)
                .build();
    }

    boolean play(TextureCue cue, float strength, boolean requestAudioFocus) {
        stop();
        if (audioManager == null || strength <= 0f) {
            return false;
        }
        if (requestAudioFocus) {
            hasAudioFocus = audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!hasAudioFocus) {
                return false;
            }
        }

        int playGeneration = generation.incrementAndGet();
        audioExecutor.execute(() -> startTrack(cue, clamp(strength), playGeneration));
        return true;
    }

    void stop() {
        generation.incrementAndGet();
        AudioTrack track = activeTrack.getAndSet(null);
        releaseTrack(track);
        abandonFocus();
    }

    void release() {
        stop();
        audioExecutor.shutdownNow();
        samples.clear();
    }

    private void startTrack(TextureCue cue, float strength, int playGeneration) {
        short[] pcm = sampleFor(cue);
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        AudioTrack track;
        try {
            track = new AudioTrack(attributes, format, pcm.length * 2,
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                mainHandler.post(this::abandonFocus);
                return;
            }
            track.write(pcm, 0, pcm.length);
            track.setVolume(strength);
        } catch (RuntimeException exception) {
            mainHandler.post(this::abandonFocus);
            return;
        }

        if (generation.get() != playGeneration) {
            releaseTrack(track);
            return;
        }
        AudioTrack previous = activeTrack.getAndSet(track);
        releaseTrack(previous);
        try {
            track.play();
        } catch (RuntimeException exception) {
            activeTrack.compareAndSet(track, null);
            releaseTrack(track);
            mainHandler.post(this::abandonFocus);
            return;
        }
        mainHandler.postDelayed(() -> finishTrack(track, playGeneration), cue.durationMs() + 90L);
    }

    private void finishTrack(AudioTrack track, int playGeneration) {
        if (generation.get() == playGeneration && activeTrack.compareAndSet(track, null)) {
            releaseTrack(track);
            abandonFocus();
        }
    }

    private void onAudioFocusChanged(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            stop();
        }
    }

    private void abandonFocus() {
        if (audioManager != null && hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            hasAudioFocus = false;
        }
    }

    private short[] sampleFor(TextureCue cue) {
        synchronized (samples) {
            short[] sample = samples.get(cue);
            if (sample == null) {
                sample = render(cue);
                samples.put(cue, sample);
            }
            return sample;
        }
    }

    private static short[] render(TextureCue cue) {
        int count = Math.max(1, (int) (SAMPLE_RATE * cue.durationMs() / 1000L));
        short[] pcm = new short[count];
        Random random = new Random(71L + cue.ordinal() * 997L);
        float filteredNoise = 0f;
        for (int i = 0; i < count; i++) {
            float t = i / (float) SAMPLE_RATE;
            float progress = i / (float) count;
            float value;
            switch (cue) {
                case LISTENING_STARTED:
                    value = airyOpen(t, progress, random);
                    break;
                case CONTENT_MOVEMENT:
                    filteredNoise = filteredNoise * 0.88f + (random.nextFloat() * 2f - 1f) * 0.12f;
                    value = filteredNoise * envelope(progress, 0.05f, 0.62f);
                    break;
                case FOCUS_ENTERED:
                    value = glass(t, progress, 1620f, 2380f, 31f);
                    break;
                case ATTENTION_URGENT:
                    value = knock(t, progress, 0.05f) + knock(t, progress, 0.52f);
                    break;
                case PROPOSAL_READY:
                    value = glass(t, progress, 720f, 1440f, 8f) * 0.72f;
                    break;
                case CONFIRMATION_REQUIRED:
                    value = hollowGlass(t, progress);
                    break;
                case EXECUTION_STARTED:
                    value = glide(t, progress);
                    break;
                case ACTION_DISPATCHED:
                    value = glass(t, progress, 1320f, 2470f, 9f)
                            + glass(t, Math.max(0f, progress - 0.28f), 1840f, 2760f, 15f) * 0.42f;
                    break;
                case ACTION_FAILED:
                    filteredNoise = filteredNoise * 0.72f + (random.nextFloat() * 2f - 1f) * 0.28f;
                    value = (float) Math.sin(2d * Math.PI * 132d * t) * envelope(progress, 0.02f, 0.45f)
                            + filteredNoise * ((progress > 0.18f && progress < 0.7f) ? 0.36f : 0.08f);
                    break;
                case CANCELLED:
                default:
                    filteredNoise = filteredNoise * 0.91f + (random.nextFloat() * 2f - 1f) * 0.09f;
                    value = filteredNoise * (1f - progress) * 0.75f
                            + (float) Math.sin(2d * Math.PI * (420d - 220d * progress) * t)
                            * envelope(progress, 0.03f, 0.6f) * 0.18f;
                    break;
            }
            pcm[i] = (short) (Short.MAX_VALUE * Math.max(-1f, Math.min(1f, value * 0.46f)));
        }
        return pcm;
    }

    private static float airyOpen(float t, float progress, Random random) {
        float rise = Math.min(1f, progress / 0.22f);
        float fade = 1f - Math.max(0f, (progress - 0.62f) / 0.38f);
        float air = (random.nextFloat() * 2f - 1f) * 0.11f;
        float tone = (float) Math.sin(2d * Math.PI * (520d + 280d * progress) * t) * 0.42f;
        return (air + tone) * rise * fade;
    }

    private static float glass(float t, float progress, float primary, float secondary, float decay) {
        float fade = (float) Math.exp(-decay * t);
        float attack = Math.min(1f, progress / 0.025f);
        return ((float) Math.sin(2d * Math.PI * primary * t)
                + 0.38f * (float) Math.sin(2d * Math.PI * secondary * t)) * fade * attack;
    }

    private static float hollowGlass(float t, float progress) {
        float first = glass(t, progress, 560f, 1120f, 8f) * 0.62f;
        float shifted = progress - 0.43f;
        float second = shifted > 0f
                ? glass(t - 0.16f, shifted, 630f, 1260f, 12f) * 0.42f
                : 0f;
        return first + second;
    }

    private static float knock(float t, float progress, float start) {
        float local = progress - start;
        if (local < 0f || local > 0.27f) {
            return 0f;
        }
        return (float) Math.sin(2d * Math.PI * 185d * t) * (1f - local / 0.27f) * 0.92f;
    }

    private static float glide(float t, float progress) {
        float frequency = 330f + 760f * progress;
        return (float) Math.sin(2d * Math.PI * frequency * t) * envelope(progress, 0.06f, 0.74f) * 0.62f;
    }

    private static float envelope(float progress, float attackEnd, float releaseStart) {
        float attack = attackEnd <= 0f ? 1f : Math.min(1f, progress / attackEnd);
        float release = progress <= releaseStart ? 1f : Math.max(0f, 1f - (progress - releaseStart) / (1f - releaseStart));
        return attack * release;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void releaseTrack(AudioTrack track) {
        if (track == null) {
            return;
        }
        try {
            track.pause();
            track.flush();
            track.release();
        } catch (RuntimeException ignored) {
            // The platform may already have invalidated the track after audio focus loss.
        }
    }
}
