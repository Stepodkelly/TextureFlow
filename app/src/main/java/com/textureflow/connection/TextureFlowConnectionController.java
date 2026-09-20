package com.textureflow.connection;

import android.content.Context;
import android.content.Intent;

import com.textureflow.notifications.NotificationRuntime;

/** Explicit integration entry point; nothing in this package starts itself. */
public final class TextureFlowConnectionController {
    private TextureFlowConnectionController() {}

    public static void configure(
            Context context,
            String convexUrl,
            String ownerId,
            String deviceActorToken,
            String oidcToken,
            String deviceDisplayName) {
        ConnectionConfigStore.saveRuntime(
                context, convexUrl, ownerId, deviceActorToken, oidcToken, deviceDisplayName);
    }

    /** Prefer this while Convex / VoiceOS are stubbed. */
    public static void useLocalStub(Context context) {
        NotificationRuntime runtime = NotificationRuntime.get(context);
        ConnectionConfigStore.enableLocalStub(context, runtime.getDeviceId());
    }

    public static void start(Context context) {
        if (!ConnectionConfigStore.isConfigured(
                context, NotificationRuntime.get(context).getDeviceId())) {
            useLocalStub(context);
        }
        Intent intent = new Intent(context, TextureFlowConnectionService.class)
                .setAction(TextureFlowConnectionService.ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, TextureFlowConnectionService.class));
    }
}
