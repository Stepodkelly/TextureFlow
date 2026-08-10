package com.textureflow.connection;

/** Time source kept injectable so recovery behavior can be tested without sleeping. */
public interface ConnectionClock {
    long nowMillis();

    ConnectionClock SYSTEM = System::currentTimeMillis;
}
