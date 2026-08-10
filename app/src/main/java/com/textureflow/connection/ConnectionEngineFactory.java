package com.textureflow.connection;

import android.content.Context;

import com.textureflow.notifications.NotificationRuntime;

public final class ConnectionEngineFactory {
    private ConnectionEngineFactory() {}

    public static ConnectionEngine create(Context context, ConnectionObserver observer) {
        Context application = context.getApplicationContext();
        NotificationRuntime runtime = NotificationRuntime.get(application);
        ConnectionConfig config = ConnectionConfigStore.load(application, runtime.getDeviceId());
        runtime.configureOwner(config.ownerId());

        ClaimStore claims = new SharedPreferencesClaimStore(application);
        RemoteGateway gateway = new ConvexHttpGateway(config);
        OutboxCoordinator outbox = new OutboxCoordinator(
                new AndroidDurableOutbox(runtime.outbox()),
                new ConvexOutboxDelivery(gateway, claims),
                new BackoffPolicy(1_000L, 300_000L, 0.20),
                8,
                120_000L);
        CommandProcessor commands = new CommandProcessor(
                gateway,
                claims,
                new AndroidConfirmedActionExecutor(application),
                config.ownerId(),
                config.deviceId());
        return new ConnectionEngine(
                config,
                gateway,
                outbox,
                commands,
                ConnectionClock.SYSTEM,
                new BackoffPolicy(1_000L, 60_000L, 0.20),
                new ConnectionStateMachine(),
                observer);
    }
}
