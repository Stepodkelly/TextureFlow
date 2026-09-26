package com.textureflow.intelligence.model;

/** Thermal / battery-saver checks that force A5 (model unavailable). */
public interface DeviceGuard {
    boolean isThermalOrBatteryBlocked();

    DeviceGuard NEVER_BLOCKED = () -> false;
}
