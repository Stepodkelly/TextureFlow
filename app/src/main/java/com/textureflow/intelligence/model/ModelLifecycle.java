package com.textureflow.intelligence.model;

import java.util.Objects;

/**
 * Lazy-loads the on-device port on the first A3/A4-style generate, unloads after
 * 300 s idle or {@link #onTrimMemory()}, and treats thermal/battery-saver as A5.
 */
public final class ModelLifecycle implements ModelPort {
    public static final long IDLE_UNLOAD_MS = 300_000L;

    private final ModelPort delegate;
    private final ModelClock clock;
    private final DeviceGuard guard;
    private boolean loaded;
    private long lastUsedMillis;

    public ModelLifecycle(ModelPort delegate) {
        this(delegate, ModelClock.SYSTEM, DeviceGuard.NEVER_BLOCKED);
    }

    public ModelLifecycle(ModelPort delegate, ModelClock clock, DeviceGuard guard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    public boolean isThermalOrBatteryBlocked() {
        return guard.isThermalOrBatteryBlocked();
    }

    /** True when a generate can be attempted. False → Stream G should take A5. */
    public synchronized boolean isAvailable() {
        if (isThermalOrBatteryBlocked()) {
            return false;
        }
        if (delegate instanceof NoModelPort) {
            return false;
        }
        return true;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized long lastUsedMillis() {
        return lastUsedMillis;
    }

    public synchronized boolean unloadIfIdle(long nowMillis) {
        if (!loaded) {
            return false;
        }
        if (nowMillis - lastUsedMillis < IDLE_UNLOAD_MS) {
            return false;
        }
        unload();
        return true;
    }

    public synchronized void onTrimMemory() {
        unload();
    }

    public synchronized void onTrimMemory(int androidLevel) {
        unload();
    }

    @Override
    public synchronized void load() throws Exception {
        if (isThermalOrBatteryBlocked()) {
            throw new ModelUnavailableException("Model blocked by thermal state or battery saver.");
        }
        if (delegate instanceof NoModelPort) {
            throw new ModelUnavailableException("No on-device model is loaded.");
        }
        if (loaded) {
            touch();
            return;
        }
        delegate.load();
        loaded = true;
        touch();
    }

    @Override
    public synchronized ModelResponse generate(ModelRequest request) throws Exception {
        if (isThermalOrBatteryBlocked()) {
            throw new ModelUnavailableException("Model blocked by thermal state or battery saver.");
        }
        if (!loaded) {
            load();
        }
        ModelResponse response = delegate.generate(request);
        touch();
        return response;
    }

    @Override
    public synchronized void unload() {
        if (loaded) {
            delegate.unload();
            loaded = false;
        } else {
            delegate.unload();
        }
    }

    @Override
    public int tokenCount(String text) {
        return delegate.tokenCount(text);
    }

    @Override
    public String runtimeName() {
        return delegate.runtimeName();
    }

    public ModelPort delegate() {
        return delegate;
    }

    private void touch() {
        lastUsedMillis = clock.nowMillis();
    }
}
