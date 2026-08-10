package com.textureflow.policy;

import android.app.Notification;
import android.content.Context;

import com.textureflow.notifications.NotificationSnapshot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class NotificationIngestionPolicy {
    private static final Set<String> SYSTEM_PACKAGES = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings"));

    private final String ownPackage;

    public NotificationIngestionPolicy(Context context) {
        ownPackage = context.getApplicationContext().getPackageName();
    }

    public boolean shouldIngest(NotificationSnapshot snapshot) {
        if (snapshot == null || snapshot.getKey() == null || snapshot.getPackageName() == null) return false;
        if (ownPackage.equals(snapshot.getPackageName()) || SYSTEM_PACKAGES.contains(snapshot.getPackageName())) {
            return false;
        }
        String category = snapshot.getCategory();
        return !Notification.CATEGORY_PROGRESS.equals(category)
                && !Notification.CATEGORY_SERVICE.equals(category)
                && !Notification.CATEGORY_TRANSPORT.equals(category);
    }
}
