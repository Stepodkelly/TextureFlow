package com.textureflow.connection;

import android.content.Context;
import android.content.Intent;

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

    public static void start(Context context) {
        Intent intent = new Intent(context, TextureFlowConnectionService.class)
                .setAction(TextureFlowConnectionService.ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, TextureFlowConnectionService.class));
    }
}
