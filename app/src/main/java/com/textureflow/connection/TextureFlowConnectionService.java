package com.textureflow.connection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public final class TextureFlowConnectionService extends Service {
    public static final String ACTION_START = "com.textureflow.connection.START";
    public static final String ACTION_STOP = "com.textureflow.connection.STOP";

    private static final String CHANNEL_ID = "textureflow_connection";
    private static final String LOG_TAG = "TextureFlowConnection";
    private static final int NOTIFICATION_ID = 0x5446;

    private ConnectionEngine engine;
    private NotificationManager notificationManager;
    private volatile ConnectionStateMachine.State lastLoggedState;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEngine();
            stopSelf();
            return START_NOT_STICKY;
        }
        promote("Starting secure device connection");
        if (engine != null) return START_STICKY;
        try {
            engine = ConnectionEngineFactory.create(this, snapshot -> {
                ConnectionStatusStore.write(this, snapshot);
                updateNotification(notificationText(snapshot));
                if (snapshot.state() != lastLoggedState) {
                    lastLoggedState = snapshot.state();
                    if (snapshot.state() == ConnectionStateMachine.State.BACKING_OFF
                            || snapshot.state() == ConnectionStateMachine.State.DEGRADED) {
                        // Backend errors may echo user data. Log only the coarse connection state.
                        Log.w(LOG_TAG, snapshot.state().name());
                    } else {
                        Log.i(LOG_TAG, snapshot.state().name());
                    }
                }
            });
            engine.start();
            return START_STICKY;
        } catch (RuntimeException invalidConfiguration) {
            updateNotification("Connection needs configuration");
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        stopEngine();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopEngine() {
        ConnectionEngine current = engine;
        engine = null;
        if (current != null) current.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @SuppressLint("ForegroundServiceType") // The integration owner adds the documented service type.
    private void promote(String text) {
        Notification notification = notification(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @SuppressLint({"MissingPermission", "NotificationPermission"})
    private void updateNotification(String text) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            notificationManager.notify(NOTIFICATION_ID, notification(text));
        } catch (RuntimeException ignored) {
            // Notification rendering cannot be allowed to terminate command polling.
        }
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("TextureFlow")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void ensureChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "TextureFlow connection", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps notification sync and confirmed actions available");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private static String notificationText(ConnectionStateMachine.Snapshot snapshot) {
        return switch (snapshot.state()) {
            case ONLINE -> "Listening for confirmed actions";
            case REGISTERING -> "Registering this phone";
            case BACKING_OFF -> "Connection interrupted; retrying";
            case DEGRADED -> "Connection recovery in progress";
            case STARTING -> "Starting secure device connection";
            case STOPPING, STOPPED -> "Connection stopped";
        };
    }
}
