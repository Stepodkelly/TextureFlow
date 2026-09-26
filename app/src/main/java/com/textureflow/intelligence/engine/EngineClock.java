package com.textureflow.intelligence.engine;

/** Injectable time so ticks and tests stay deterministic. */
@FunctionalInterface
public interface EngineClock {
    EngineClock SYSTEM = System::currentTimeMillis;

    long nowMillis();
}
