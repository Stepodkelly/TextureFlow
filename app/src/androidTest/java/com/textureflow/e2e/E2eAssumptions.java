package com.textureflow.e2e;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import com.textureflow.data.NotificationRepository;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.notifications.NotificationRuntime;
import com.textureflow.notifications.TextureNotificationListenerService;

import java.util.List;

final class E2eAssumptions {
    private static final String LISTENER_CLASS =
            "com.textureflow.notifications.TextureNotificationListenerService";

    private E2eAssumptions() {}

    static boolean listenerEnabled(Context targetContext) {
        String enabled = Settings.Secure.getString(
                targetContext.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName component = new ComponentName(targetContext.getPackageName(), LISTENER_CLASS);
        return enabled.contains(component.flattenToString())
                || enabled.contains(component.flattenToShortString())
                || enabled.contains(targetContext.getPackageName() + "/" + LISTENER_CLASS);
    }

    static boolean canPostNotifications(Context testContext) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return testContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean deviceNotificationsObservable(Context targetContext) {
        return listenerEnabled(targetContext)
                || TextureNotificationListenerService.hasLiveConnection();
    }

    static StoredNotificationEvent waitForLiveByToken(
            Context targetContext, String token, long timeoutMs) {
        return waitFor(targetContext, token, true, timeoutMs);
    }

    static StoredNotificationEvent waitUntilNotLive(
            Context targetContext, String token, long timeoutMs) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        NotificationRepository repository = NotificationRuntime.get(targetContext).notifications();
        StoredNotificationEvent last = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            last = findByToken(repository.getRecentEvents(80), token);
            if (last == null || !last.isLive()) return last;
            SystemClock.sleep(200);
        }
        return last != null && !last.isLive() ? last : null;
    }

    static StoredNotificationEvent findLiveByToken(Context targetContext, String token) {
        return findByToken(
                NotificationRuntime.get(targetContext).notifications().getLiveEvents(), token);
    }

    private static StoredNotificationEvent waitFor(
            Context targetContext, String token, boolean live, long timeoutMs) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        NotificationRepository repository = NotificationRuntime.get(targetContext).notifications();
        while (SystemClock.elapsedRealtime() < deadline) {
            List<StoredNotificationEvent> rows = live
                    ? repository.getLiveEvents()
                    : repository.getRecentEvents(80);
            StoredNotificationEvent match = findByToken(rows, token);
            if (match != null && (!live || match.isLive())) {
                return match;
            }
            SystemClock.sleep(200);
        }
        return null;
    }

    private static StoredNotificationEvent findByToken(
            List<StoredNotificationEvent> rows, String token) {
        if (rows == null || token == null) return null;
        for (StoredNotificationEvent event : rows) {
            if (matchesToken(event, token)) return event;
        }
        return null;
    }

    static boolean matchesToken(StoredNotificationEvent event, String token) {
        if (event == null || token == null) return false;
        return token.equals(event.getConversationLabel())
                || (event.getConversationLabel() != null && event.getConversationLabel().contains(token))
                || (event.getNotificationKey() != null && event.getNotificationKey().contains(token));
    }
}
