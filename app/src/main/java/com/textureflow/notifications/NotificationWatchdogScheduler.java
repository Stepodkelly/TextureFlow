package com.textureflow.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/** AlarmManager heartbeat so listener recovery continues without the FGS. */
public final class NotificationWatchdogScheduler {
    public static final String ACTION = "com.textureflow.action.LISTENER_WATCHDOG";
    private static final int REQUEST_CODE = 0x544657; // TFW
    private static final long INTERVAL_MS = 30_000L;

    private NotificationWatchdogScheduler() {}

    public static void schedule(Context context) {
        Context application = context.getApplicationContext();
        try {
            AlarmManager alarms = application.getSystemService(AlarmManager.class);
            if (alarms == null) return;
            PendingIntent pending = pendingIntent(application);
            long triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
            // One-shot + reschedule in the receiver. setInexactRepeating is heavily deferred in
            // Doze and can leave the listener dead for far longer than ~1 minute after process death.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
            } else {
                alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
            }
        } catch (RuntimeException ignored) {
            // JobScheduler + ConnectionService still cover recovery paths.
        }
    }

    static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, NotificationWatchdogReceiver.class)
                .setAction(ACTION)
                .setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
