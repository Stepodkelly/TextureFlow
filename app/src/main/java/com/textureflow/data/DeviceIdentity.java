package com.textureflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class DeviceIdentity {
    private static final String PREFERENCES = "textureflow-device";
    private static final String DEVICE_ID = "device-id";

    private DeviceIdentity() {}

    public static String getOrCreate(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String existing = preferences.getString(DEVICE_ID, null);
        if (existing != null && !existing.isEmpty()) return existing;
        String created = "android_" + UUID.randomUUID().toString().replace("-", "");
        if (!preferences.edit().putString(DEVICE_ID, created).commit()) {
            throw new IllegalStateException("Could not persist TextureFlow device identity");
        }
        return created;
    }
}
