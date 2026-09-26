package com.textureflow.intelligence.model;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import java.util.Objects;

/** Battery saver or thermal SEVERE+ → model unavailable (A5). */
public final class AndroidDeviceGuard implements DeviceGuard {
    private final Context context;

    public AndroidDeviceGuard(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public boolean isThermalOrBatteryBlocked() {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power == null) {
            return false;
        }
        if (power.isPowerSaveMode()) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
        }
        return false;
    }
}
