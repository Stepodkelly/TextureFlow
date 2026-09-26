package com.textureflow.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Intelligence toggle stored in the existing sensory preference file.
 * On by default. Off detaches the UI binder and skips capture enqueue.
 */
public final class IntelligencePreferences {
    public static final String PREFERENCES = "texture_surface_preferences";
    public static final String KEY_MODE = "intelligence_mode";
    public static final boolean DEFAULT_MODE = true;

    private IntelligencePreferences() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MODE, DEFAULT_MODE);
    }

    public static boolean isEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_MODE;
        }
        return preferences.getBoolean(KEY_MODE, DEFAULT_MODE);
    }

    public static void persist(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MODE, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
