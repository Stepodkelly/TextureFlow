package com.textureflow.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.textureflow.notifications.EventVersionPolicy;
import com.textureflow.notifications.NormalizedNotification;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NotificationRepository {
    private final TextureFlowDatabase database;

    public NotificationRepository(TextureFlowDatabase database) {
        this.database = database;
    }

    public EventWriteResult upsertActive(NormalizedNotification incoming, long now) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            StoredNotificationEvent previous = readEvent(db, incoming.getEventId());
            int previousVersion = previous == null ? 0 : previous.getVersion();
            String previousStatus = previous == null ? null : previous.getStatus();
            String previousHash = previous == null ? null : previous.getContentHash();
            int version = EventVersionPolicy.nextVersion(
                    previousVersion, previousStatus, previousHash, incoming.getContentHash());
            String status = EventVersionPolicy.nextStatus(
                    previousVersion, previousStatus, previousHash, incoming.getContentHash());

            StoredNotificationEvent event = new StoredNotificationEvent(
                    incoming.getEventId(), incoming.getDeviceId(), incoming.getNotificationKey(),
                    incoming.getPackageName(), incoming.getAppLabel(), incoming.getSenderName(),
                    incoming.getConversationLabel(), incoming.getBody(), incoming.getPostedAt(), now,
                    version, status, incoming.getCapabilities(), incoming.getContentHash(),
                    incoming.getActionFingerprint(), incoming.getPriorityScore(), incoming.getPriorityLevel(),
                    incoming.getPriorityReason());

            ContentValues values = values(event, now);
            db.insertWithOnConflict(
                    "notification_events", null, values, SQLiteDatabase.CONFLICT_REPLACE);

            EventWriteResult.Change change;
            if (previous == null) {
                change = EventWriteResult.Change.INSERTED;
            } else if (version != previousVersion || !status.equals(previousStatus)) {
                change = EventWriteResult.Change.UPDATED;
            } else {
                change = EventWriteResult.Change.UNCHANGED;
            }

            if (change != EventWriteResult.Change.UNCHANGED) {
                enqueueEvent(db, event, now);
            }
            db.setTransactionSuccessful();
            return new EventWriteResult(event, change);
        } finally {
            db.endTransaction();
        }
    }

    public EventWriteResult markRemoved(String eventId, long now) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            StoredNotificationEvent previous = readEvent(db, eventId);
            if (previous == null || "REMOVED".equals(previous.getStatus())) {
                db.setTransactionSuccessful();
                return previous == null ? null : new EventWriteResult(previous, EventWriteResult.Change.UNCHANGED);
            }
            StoredNotificationEvent removed = copyWithStatus(previous, previous.getVersion() + 1, "REMOVED", now);
            db.insertWithOnConflict(
                    "notification_events", null, values(removed, now), SQLiteDatabase.CONFLICT_REPLACE);
            enqueueEvent(db, removed, now);
            db.setTransactionSuccessful();
            return new EventWriteResult(removed, EventWriteResult.Change.REMOVED);
        } finally {
            db.endTransaction();
        }
    }

    public List<StoredNotificationEvent> markMissingRemoved(Set<String> activeEventIds, long now) {
        Set<String> active = activeEventIds == null ? Collections.emptySet() : activeEventIds;
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            List<StoredNotificationEvent> missing = new ArrayList<>();
            try (Cursor cursor = db.query(
                    "notification_events", null, "status != ?", new String[] {"REMOVED"},
                    null, null, null)) {
                while (cursor.moveToNext()) {
                    StoredNotificationEvent previous = fromCursor(cursor);
                    if (!active.contains(previous.getEventId())) {
                        missing.add(previous);
                    }
                }
            }
            List<StoredNotificationEvent> removed = new ArrayList<>();
            for (StoredNotificationEvent previous : missing) {
                StoredNotificationEvent tombstone = copyWithStatus(
                        previous, previous.getVersion() + 1, "REMOVED", now);
                db.insertWithOnConflict(
                        "notification_events", null, values(tombstone, now), SQLiteDatabase.CONFLICT_REPLACE);
                enqueueEvent(db, tombstone, now);
                removed.add(tombstone);
            }
            db.setTransactionSuccessful();
            return removed;
        } finally {
            db.endTransaction();
        }
    }

    public StoredNotificationEvent getEvent(String eventId) {
        return readEvent(database.getReadableDatabase(), eventId);
    }

    public List<StoredNotificationEvent> getLiveEvents() {
        List<StoredNotificationEvent> result = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                "notification_events", null, "status != ?", new String[] {"REMOVED"},
                null, null, "updated_at DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    /** Recent notification snapshots used for the local, notification-derived People timeline. */
    public List<StoredNotificationEvent> getRecentEvents(int limit) {
        List<StoredNotificationEvent> result = new ArrayList<>();
        int boundedLimit = Math.max(1, Math.min(250, limit));
        try (Cursor cursor = database.getReadableDatabase().query(
                "notification_events", null, null, null,
                null, null, "updated_at DESC", String.valueOf(boundedLimit))) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    private static StoredNotificationEvent readEvent(SQLiteDatabase db, String eventId) {
        try (Cursor cursor = db.query(
                "notification_events", null, "event_id = ?", new String[] {eventId},
                null, null, null, "1")) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    private static StoredNotificationEvent fromCursor(Cursor cursor) {
        return new StoredNotificationEvent(
                text(cursor, "event_id"), text(cursor, "device_id"), text(cursor, "notification_key"),
                text(cursor, "package_name"), text(cursor, "app_label"), text(cursor, "sender_name"),
                nullableText(cursor, "conversation_label"), nullableText(cursor, "body"),
                number(cursor, "posted_at"), number(cursor, "updated_at"),
                integer(cursor, "version"), text(cursor, "status"),
                decodeCapabilities(text(cursor, "capabilities")), text(cursor, "content_hash"),
                text(cursor, "action_fingerprint"), decimal(cursor, "priority_score"),
                text(cursor, "priority_level"), text(cursor, "priority_reason"));
    }

    private static ContentValues values(StoredNotificationEvent event, long lastSeenAt) {
        ContentValues values = new ContentValues();
        values.put("event_id", event.getEventId());
        values.put("device_id", event.getDeviceId());
        values.put("notification_key", event.getNotificationKey());
        values.put("package_name", event.getPackageName());
        values.put("app_label", event.getAppLabel());
        values.put("sender_name", event.getSenderName());
        values.put("conversation_label", event.getConversationLabel());
        values.put("body", event.getBody());
        values.put("posted_at", event.getPostedAt());
        values.put("updated_at", event.getUpdatedAt());
        values.put("last_seen_at", lastSeenAt);
        values.put("version", event.getVersion());
        values.put("status", event.getStatus());
        values.put("capabilities", encodeCapabilities(event.getCapabilities()));
        values.put("content_hash", event.getContentHash());
        values.put("action_fingerprint", event.getActionFingerprint());
        values.put("priority_score", event.getPriorityScore());
        values.put("priority_level", event.getPriorityLevel());
        values.put("priority_reason", event.getPriorityReason());
        return values;
    }

    private static StoredNotificationEvent copyWithStatus(
            StoredNotificationEvent previous, int version, String status, long now) {
        return new StoredNotificationEvent(
                previous.getEventId(), previous.getDeviceId(), previous.getNotificationKey(),
                previous.getPackageName(), previous.getAppLabel(), previous.getSenderName(),
                previous.getConversationLabel(), previous.getBody(), previous.getPostedAt(), now,
                version, status, previous.getCapabilities(), previous.getContentHash(),
                previous.getActionFingerprint(), previous.getPriorityScore(), previous.getPriorityLevel(),
                previous.getPriorityReason());
    }

    private static void enqueueEvent(SQLiteDatabase db, StoredNotificationEvent event, long now) {
        try {
            TextureFlowDatabase.enqueue(
                    db,
                    "event:" + event.getEventId() + ":" + event.getVersion() + ":" + event.getStatus(),
                    "EVENT_UPSERT",
                    event.getEventId(),
                    event.toContractJson().toString(),
                    now);
        } catch (JSONException invalidContract) {
            throw new IllegalStateException("Could not serialize notification contract", invalidContract);
        }
    }

    private static String encodeCapabilities(Set<String> capabilities) {
        return String.join(",", capabilities);
    }

    private static Set<String> decodeCapabilities(String encoded) {
        Set<String> result = new LinkedHashSet<>();
        if (encoded == null || encoded.isEmpty()) return result;
        Collections.addAll(result, encoded.split(","));
        return result;
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static String nullableText(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static long number(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    private static int integer(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static double decimal(Cursor cursor, String column) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(column));
    }
}
