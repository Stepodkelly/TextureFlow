package com.textureflow.intelligence.model;

import android.content.Context;
import android.content.pm.PackageManager;

/** Probes Gemini Nano / AICore without depending on those APIs. */
public final class PlatformModelProbe {
    private PlatformModelProbe() {}

    public static boolean aicorePresent(Context context) {
        PackageManager packages = context.getPackageManager();
        return packageInstalled(packages, "com.google.android.aicore")
                || packageInstalled(packages, "com.google.android.gms.aicore");
    }

    private static boolean packageInstalled(PackageManager packages, String name) {
        try {
            packages.getPackageInfo(name, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }
}
