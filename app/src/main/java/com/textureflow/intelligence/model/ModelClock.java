package com.textureflow.intelligence.model;

/** Injectable time source so lifecycle idle unload can be tested without sleeping. */
public interface ModelClock {
    long nowMillis();

    ModelClock SYSTEM = System::currentTimeMillis;
}
