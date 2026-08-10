package com.textureflow.connection;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, non-secret health snapshot shared between the foreground service and activity. */
public final class ConnectionStatusStore {
    public record Snapshot(String state, String detail, long changedAtMillis, long lastOnlineAtMillis) {}

    private static final String PREFERENCES = "textureflow-connection-health";
    private static final String STATE = "state";
    private static final String DETAIL = "detail";
    private static final String CHANGED_AT = "changed-at";
    private static final String LAST_ONLINE_AT = "last-online-at";
    private static final long ONLINE_WRITE_INTERVAL_MS = 5_000L;

    private ConnectionStatusStore() {}

    public static void write(Context context, ConnectionStateMachine.Snapshot snapshot) {
        SharedPreferences values = preferences(context);
        long lastOnlineAt = values.getLong(LAST_ONLINE_AT, 0L);
        if (snapshot.state() == ConnectionStateMachine.State.ONLINE) {
            lastOnlineAt = snapshot.changedAtMillis();
            long lastWrite = values.getLong(CHANGED_AT, 0L);
            if (snapshot.changedAtMillis() - lastWrite < ONLINE_WRITE_INTERVAL_MS
                    && "ONLINE".equals(values.getString(STATE, ""))) {
                return;
            }
        }
        values.edit()
                .putString(STATE, snapshot.state().name())
                .putString(DETAIL, snapshot.detail())
                .putLong(CHANGED_AT, snapshot.changedAtMillis())
                .putLong(LAST_ONLINE_AT, lastOnlineAt)
                .apply();
    }

    public static Snapshot read(Context context) {
        SharedPreferences values = preferences(context);
        return new Snapshot(
                values.getString(STATE, "STOPPED"),
                values.getString(DETAIL, "Core link is stopped"),
                values.getLong(CHANGED_AT, 0L),
                values.getLong(LAST_ONLINE_AT, 0L));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
