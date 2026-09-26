package com.textureflow.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class TextureFlowDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "textureflow-notifications.db";
    private static final int DATABASE_VERSION = 2;

    /**
     * Schema v2 statements executed by {@link #migrateTo2}. Package-visible so unit tests can
     * assert table/column names without opening SQLite.
     */
    static final String[] V2_STATEMENTS = {
            "CREATE TABLE attention_ticks ("
                    + "tick_id TEXT PRIMARY KEY, trigger TEXT NOT NULL, person_id TEXT NOT NULL, "
                    + "package_name TEXT NOT NULL, started_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL, "
                    + "escalation_rule TEXT NOT NULL, tier TEXT NOT NULL, source TEXT NOT NULL)",
            "CREATE INDEX attention_ticks_started_idx ON attention_ticks(started_at)",

            "CREATE TABLE assessments ("
                    + "tick_id TEXT PRIMARY KEY, level TEXT NOT NULL, system_confidence REAL NOT NULL, "
                    + "model_confidence REAL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL)",
            "CREATE TABLE role_runs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, tick_id TEXT NOT NULL, role TEXT NOT NULL, "
                    + "prompt_version TEXT NOT NULL, input_tokens INTEGER NOT NULL, output_tokens INTEGER NOT NULL, "
                    + "wall_ms INTEGER NOT NULL, schema_valid INTEGER NOT NULL, skeptic_issues TEXT)",
            "CREATE INDEX role_runs_tick_idx ON role_runs(tick_id)",

            "CREATE TABLE proposals ("
                    + "proposal_id TEXT PRIMARY KEY, event_id TEXT NOT NULL, event_version INTEGER NOT NULL, "
                    + "package_name TEXT NOT NULL, person_id TEXT NOT NULL, action_type TEXT NOT NULL, "
                    + "payload_hash TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, "
                    + "expires_at INTEGER NOT NULL, payload_text TEXT)",
            "CREATE INDEX proposals_status_idx ON proposals(status, expires_at)",

            "CREATE TABLE capability_profile ("
                    + "singleton_id INTEGER PRIMARY KEY CHECK(singleton_id = 1), "
                    + "profile_json TEXT NOT NULL, updated_at INTEGER NOT NULL)",

            // Reserved Part II tables (doc §34). Empty and unused in v1.
            "CREATE TABLE nodes ("
                    + "node_id TEXT PRIMARY KEY, tier TEXT NOT NULL, cap_tier TEXT NOT NULL, "
                    + "public_key TEXT NOT NULL, last_seen_at INTEGER NOT NULL, status TEXT NOT NULL, "
                    + "capabilities_json TEXT NOT NULL)",
            "CREATE TABLE compute_sessions ("
                    + "session_id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER, "
                    + "end_reason TEXT, node_ids TEXT NOT NULL)",
            "CREATE TABLE depth_jobs ("
                    + "job_id TEXT PRIMARY KEY, tick_id TEXT NOT NULL, job_class TEXT NOT NULL, "
                    + "data_class TEXT NOT NULL, state TEXT NOT NULL, shard_map_json TEXT, "
                    + "effective_params_b REAL NOT NULL, bytes_transferred INTEGER NOT NULL, "
                    + "seed_index INTEGER NOT NULL, cancelled_late INTEGER NOT NULL, wall_ms INTEGER NOT NULL)",
            "CREATE TABLE boundary_reports ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, job_id TEXT NOT NULL, boundary INTEGER NOT NULL, "
                    + "on_time INTEGER NOT NULL, h_mean REAL NOT NULL, continue_depth INTEGER NOT NULL, "
                    + "recorded_at INTEGER NOT NULL)",
            "CREATE TABLE tick_outcomes ("
                    + "tick_id TEXT PRIMARY KEY, exit_plane TEXT NOT NULL, exit_layer INTEGER, "
                    + "source TEXT NOT NULL, total_latency_ms INTEGER NOT NULL, replaced_reflex INTEGER NOT NULL)"
    };

    public TextureFlowDatabase(Context context) {
        this(context, DATABASE_NAME);
    }

    /** Named file, or {@code null} for an in-memory database (tests). */
    public TextureFlowDatabase(Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createV1Schema(db);
        migrateTo2(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        int version = oldVersion;
        if (version < 2 && newVersion >= 2) {
            migrateTo2(db);
            version = 2;
        }
        if (version != newVersion) {
            throw new IllegalStateException("No database migration exists from " + oldVersion + " to " + newVersion);
        }
    }

    private static void createV1Schema(SQLiteDatabase db) {
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

    /** Creates intelligence ledger tables plus reserved unused Part II tables. */
    static void migrateTo2(SQLiteDatabase db) {
        for (String sql : V2_STATEMENTS) {
            db.execSQL(sql);
        }
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
