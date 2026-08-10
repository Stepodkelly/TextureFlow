package com.textureflow;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import java.util.Objects;

/** Detects an intentional shake without retaining an Activity or requiring dependencies. */
public final class ShakeUrgencyController implements SensorEventListener {
    public interface Callback {
        void onShake();
    }

    private static final float SHAKE_THRESHOLD = SensorManager.GRAVITY_EARTH * 1.25f;
    private static final float RESET_THRESHOLD = SensorManager.GRAVITY_EARTH * 0.65f;
    private static final long MIN_PEAK_GAP_NANOS = 80_000_000L;
    private static final long PEAK_WINDOW_NANOS = 650_000_000L;
    private static final long DEBOUNCE_NANOS = 1_500_000_000L;
    private static final float GRAVITY_FILTER_ALPHA = 0.82f;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final float[] gravity = new float[3];

    private Callback callback;
    private boolean running;
    private boolean released;
    private boolean gravityInitialized;
    private boolean aboveThreshold;
    private long firstPeakNanos;
    private long lastShakeNanos;

    public ShakeUrgencyController(Context context, Callback callback) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callback = Objects.requireNonNull(callback, "callback");
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Starts detection. Returns false when acceleration sensing is unavailable. */
    public synchronized boolean start() {
        if (released || accelerometer == null || sensorManager == null) {
            return false;
        }
        if (running) {
            return true;
        }

        resetDetectionState();
        running = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME,
                mainHandler
        );
        return running;
    }

    public synchronized void stop() {
        if (sensorManager != null && running) {
            sensorManager.unregisterListener(this);
        }
        running = false;
        resetDetectionState();
    }

    /** Permanently stops detection and releases the callback reference. */
    public synchronized void release() {
        stop();
        released = true;
        callback = null;
    }

    public boolean isAvailable() {
        return accelerometer != null;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean detected;
        synchronized (this) {
            detected = running
                    && !released
                    && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER
                    && detectShake(event.values, event.timestamp);
        }
        if (detected) {
            dispatchShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No recalibration is needed; the gravity filter adapts continuously.
    }

    private boolean detectShake(float[] values, long nowNanos) {
        if (!gravityInitialized) {
            System.arraycopy(values, 0, gravity, 0, gravity.length);
            gravityInitialized = true;
            return false;
        }

        float magnitudeSquared = 0f;
        for (int i = 0; i < gravity.length; i++) {
            gravity[i] = GRAVITY_FILTER_ALPHA * gravity[i]
                    + (1f - GRAVITY_FILTER_ALPHA) * values[i];
            float linearAcceleration = values[i] - gravity[i];
            magnitudeSquared += linearAcceleration * linearAcceleration;
        }
        float magnitude = (float) Math.sqrt(magnitudeSquared);

        if (magnitude < RESET_THRESHOLD) {
            aboveThreshold = false;
        }
        if (firstPeakNanos != 0L && nowNanos - firstPeakNanos > PEAK_WINDOW_NANOS) {
            firstPeakNanos = 0L;
        }
        if (magnitude < SHAKE_THRESHOLD || aboveThreshold) {
            return false;
        }

        aboveThreshold = true;
        if (lastShakeNanos != 0L && nowNanos - lastShakeNanos < DEBOUNCE_NANOS) {
            return false;
        }
        if (firstPeakNanos == 0L) {
            firstPeakNanos = nowNanos;
            return false;
        }

        long peakGap = nowNanos - firstPeakNanos;
        if (peakGap < MIN_PEAK_GAP_NANOS) {
            return false;
        }

        firstPeakNanos = 0L;
        lastShakeNanos = nowNanos;
        return true;
    }

    private void dispatchShake() {
        mainHandler.post(() -> {
            Callback currentCallback;
            synchronized (ShakeUrgencyController.this) {
                if (!running || released) {
                    return;
                }
                currentCallback = callback;
            }
            if (currentCallback != null) {
                currentCallback.onShake();
            }
        });
    }

    private void resetDetectionState() {
        gravityInitialized = false;
        aboveThreshold = false;
        firstPeakNanos = 0L;
        lastShakeNanos = 0L;
    }
}
