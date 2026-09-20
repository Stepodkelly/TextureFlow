package com.textureflow.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.textureflow.data.ListenerHealthStore;

/**
 * Periodic listener durability check independent of JobScheduler / FGS timing.
 * Action: {@link NotificationWatchdogScheduler#ACTION}.
 */
public final class NotificationWatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !NotificationWatchdogScheduler.ACTION.equals(intent.getAction())) {
            return;
        }
        Context application = context.getApplicationContext();
        NotificationWatchdogScheduler.schedule(application);
        long now = System.currentTimeMillis();
        if (!NotificationHealthJobService.hasNotificationAccess(application)) {
            NotificationRuntime.get(application).health()
                    .markStale(now, "notification access revoked");
            return;
        }
        ListenerHealthStore.Snapshot health = NotificationRuntime.get(application).health().read();
        boolean live = TextureNotificationListenerService.hasLiveConnection();
        if (ListenerHealthPolicy.needsForceRestart(health, now, live)) {
            TextureNotificationListenerService.forceStaleRebind(application);
        } else {
            TextureNotificationListenerService.requestHealthReconciliation(application);
        }
    }
}
