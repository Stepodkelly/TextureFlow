package com.textureflow.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.textureflow.connection.ConnectionConfigStore;
import com.textureflow.connection.TextureFlowConnectionController;

public final class NotificationRecoveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHealthJobService.schedule(context);
        TextureNotificationListenerService.requestRebindNow(context);
        NotificationRuntime runtime = NotificationRuntime.get(context);
        if (ConnectionConfigStore.isConfigured(context, runtime.getDeviceId())) {
            try {
                TextureFlowConnectionController.start(context);
            } catch (RuntimeException unavailable) {
                // The listener and health job remain active; the next allowed resume retries Core.
            }
        }
    }
}
