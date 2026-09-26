package com.textureflow.intelligence.model;

final class MutableModelClock implements ModelClock {
    long now;

    MutableModelClock(long now) {
        this.now = now;
    }

    @Override
    public long nowMillis() {
        return now;
    }
}
