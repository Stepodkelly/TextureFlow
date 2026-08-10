package com.textureflow.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public final class OutboxStore {
    private final TextureFlowDatabase database;

    public OutboxStore(TextureFlowDatabase database) {
        this.database = database;
    }

    public List<OutboxRecord> claimBatch(int requestedLimit, long now, long leaseDurationMs) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            List<OutboxRecord> result = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT id, operation_key, kind, aggregate_id, payload, attempts "
                            + "FROM pending_sync_operations WHERE "
                            + "(state = 'PENDING' AND available_at <= ?) OR "
                            + "(state = 'IN_FLIGHT' AND lease_until <= ?) "
                            + "ORDER BY id LIMIT ?",
                    new String[] {String.valueOf(now), String.valueOf(now), String.valueOf(limit)})) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    result.add(new OutboxRecord(
                            id, cursor.getString(1), cursor.getString(2), cursor.getString(3),
                            cursor.getString(4), cursor.getInt(5) + 1));
                    db.execSQL(
                            "UPDATE pending_sync_operations SET state = 'IN_FLIGHT', attempts = attempts + 1, "
                                    + "lease_until = ? WHERE id = ?",
                            new Object[] {now + Math.max(1_000L, leaseDurationMs), id});
                }
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    public void acknowledge(long id) {
        database.getWritableDatabase().delete(
                "pending_sync_operations", "id = ?", new String[] {String.valueOf(id)});
    }

    public void release(long id, String error, long retryAt) {
        database.getWritableDatabase().execSQL(
                "UPDATE pending_sync_operations SET state = 'PENDING', available_at = ?, lease_until = 0, "
                        + "last_error = ? WHERE id = ?",
                new Object[] {retryAt, truncate(error, 500), id});
    }

    public int pendingCount() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM pending_sync_operations", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
