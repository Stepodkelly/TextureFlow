package com.textureflow.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public final class ListenerHealthStore {
    public static final class Snapshot {
        public final boolean connected;
        public final long lastConnectedAt;
        public final long lastCallbackAt;
        public final long lastReconciledAt;
        public final int lastActiveCount;
        public final int consecutiveFailures;
        public final String lastError;

        public Snapshot(boolean connected, long lastConnectedAt, long lastCallbackAt, long lastReconciledAt,
                 int lastActiveCount, int consecutiveFailures, String lastError) {
            this.connected = connected;
            this.lastConnectedAt = lastConnectedAt;
            this.lastCallbackAt = lastCallbackAt;
            this.lastReconciledAt = lastReconciledAt;
            this.lastActiveCount = lastActiveCount;
            this.consecutiveFailures = consecutiveFailures;
            this.lastError = lastError;
        }
    }

    private final TextureFlowDatabase database;

    public ListenerHealthStore(TextureFlowDatabase database) {
        this.database = database;
    }

    public void connected(long now) {
        // Reset last_callback_at so post-connect grace is real; do not inherit markStale/disconnect times.
        database.getWritableDatabase().execSQL(
                "UPDATE listener_health SET connected = 1, last_connected_at = ?, last_callback_at = 0, "
                        + "consecutive_failures = 0, last_error = NULL WHERE singleton_id = 1",
                new Object[] {now});
    }

    public void disconnected(long now, String reason) {
        // Never touch last_callback_at — that column is only for real posted/removed callbacks.
        database.getWritableDatabase().execSQL(
                "UPDATE listener_health SET connected = 0, last_error = ? "
                        + "WHERE singleton_id = 1", new Object[] {truncate(reason)});
    }

    /** Clears the sticky connected flag when an external freshness watchdog proves the listener dead. */
    public void markStale(long now, String reason) {
        // Must not forge last_callback_at; that would make zombie health look fresh after force.
        database.getWritableDatabase().execSQL(
                "UPDATE listener_health SET connected = 0, last_error = ? "
                        + "WHERE singleton_id = 1", new Object[] {truncate(reason)});
    }

    public void callback(long now) {
        database.getWritableDatabase().execSQL(
                "UPDATE listener_health SET last_callback_at = ? WHERE singleton_id = 1", new Object[] {now});
    }

    public void reconciled(long now, int activeCount) {
        database.getWritableDatabase().execSQL(
                "UPDATE listener_health SET connected = 1, last_reconciled_at = ?, last_active_count = ?, "
                        + "consecutive_failures = 0, last_error = NULL WHERE singleton_id = 1",
                new Object[] {now, activeCount});
    }

    public void failed(long now, Throwable error) {
        SQLiteDatabase db = database.getWritableDatabase();
        // Failures are not callbacks — forging last_callback_at hides zombie lockouts.
        db.execSQL(
                "UPDATE listener_health SET consecutive_failures = consecutive_failures + 1, "
                        + "last_error = ? WHERE singleton_id = 1",
                new Object[] {truncate(error == null ? "unknown failure" : error.toString())});
        db.execSQL(
                "UPDATE listener_health SET connected = 0 WHERE singleton_id = 1 AND consecutive_failures >= 3");
    }

    public Snapshot read() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT connected, last_connected_at, last_callback_at, last_reconciled_at, last_active_count, "
                        + "consecutive_failures, last_error FROM listener_health WHERE singleton_id = 1", null)) {
            if (!cursor.moveToFirst()) {
                return new Snapshot(false, 0, 0, 0, 0, 0, "health row missing");
            }
            return new Snapshot(
                    cursor.getInt(0) != 0, cursor.getLong(1), cursor.getLong(2), cursor.getLong(3),
                    cursor.getInt(4), cursor.getInt(5), cursor.isNull(6) ? null : cursor.getString(6));
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 500) return value;
        return value.substring(0, 500);
    }
}
