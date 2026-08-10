package com.textureflow.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class TextureFlowDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "textureflow-notifications.db";
    private static final int DATABASE_VERSION = 1;

    public TextureFlowDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notification_events ("
                + "event_id TEXT PRIMARY KEY, device_id TEXT NOT NULL, notification_key TEXT NOT NULL, "
                + "package_name TEXT NOT NULL, app_label TEXT NOT NULL, sender_name TEXT NOT NULL, "
                + "conversation_label TEXT, body TEXT, posted_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, "
                + "last_seen_at INTEGER NOT NULL, version INTEGER NOT NULL, status TEXT NOT NULL, "
                + "capabilities TEXT NOT NULL, content_hash TEXT NOT NULL, action_fingerprint TEXT NOT NULL, "
                + "priority_score REAL NOT NULL, priority_level TEXT NOT NULL, priority_reason TEXT NOT NULL)");
        db.execSQL("CREATE INDEX notification_events_status_idx ON notification_events(status, last_seen_at)");

        db.execSQL("CREATE TABLE pending_sync_operations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, operation_key TEXT NOT NULL UNIQUE, kind TEXT NOT NULL, "
                + "aggregate_id TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, "
                + "available_at INTEGER NOT NULL, lease_until INTEGER NOT NULL, created_at INTEGER NOT NULL, "
                + "last_error TEXT)");
        db.execSQL("CREATE INDEX pending_sync_ready_idx ON pending_sync_operations(state, available_at, lease_until)");

        db.execSQL("CREATE TABLE processed_commands ("
                + "command_id TEXT PRIMARY KEY, idempotency_key TEXT NOT NULL UNIQUE, proposal_id TEXT NOT NULL, "
                + "state TEXT NOT NULL, receipt_id TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE action_receipts ("
                + "receipt_id TEXT PRIMARY KEY, command_id TEXT NOT NULL UNIQUE, device_id TEXT NOT NULL, "
                + "status TEXT NOT NULL, error_code TEXT, message TEXT NOT NULL, device_timestamp TEXT NOT NULL, "
                + "texture_cue TEXT NOT NULL, trace_id TEXT NOT NULL, created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE listener_health ("
                + "singleton_id INTEGER PRIMARY KEY CHECK(singleton_id = 1), connected INTEGER NOT NULL, "
                + "last_connected_at INTEGER NOT NULL, last_callback_at INTEGER NOT NULL, "
                + "last_reconciled_at INTEGER NOT NULL, last_active_count INTEGER NOT NULL, "
                + "consecutive_failures INTEGER NOT NULL, last_error TEXT)");
        db.execSQL("INSERT INTO listener_health(singleton_id, connected, last_connected_at, last_callback_at, "
                + "last_reconciled_at, last_active_count, consecutive_failures, last_error) "
                + "VALUES(1, 0, 0, 0, 0, 0, 0, NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No database migration exists from " + oldVersion + " to " + newVersion);
    }

    static void enqueue(
            SQLiteDatabase db,
            String operationKey,
            String kind,
            String aggregateId,
            String payload,
            long now) {
        db.execSQL(
                "INSERT OR IGNORE INTO pending_sync_operations("
                        + "operation_key, kind, aggregate_id, payload, state, attempts, available_at, lease_until, created_at) "
                        + "VALUES(?, ?, ?, ?, 'PENDING', 0, ?, 0, ?)",
                new Object[] {operationKey, kind, aggregateId, payload, now, now});
    }
}
