package com.textureflow.bank;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** v1 durable bank storage via SharedPreferences (avoids SQLite migration). */
public final class BankStore {
    private static final String PREFS = "textureflow-notification-bank";
    private static final String ENTRIES = "entries";

    private final SharedPreferences preferences;

    public BankStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    /** Test / injection constructor. */
    public BankStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is required");
        this.preferences = preferences;
    }

    public synchronized void upsert(BankEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        try {
            JSONObject root = readRoot();
            root.put(entry.key().storageKey(), toJson(entry));
            if (!preferences.edit().putString(ENTRIES, root.toString()).commit()) {
                throw new IllegalStateException("Could not persist bank entry");
            }
        } catch (JSONException failure) {
            throw new IllegalStateException("Could not serialize bank entry", failure);
        }
    }

    public synchronized BankEntry peek(BankKey key) {
        if (key == null) return null;
        try {
            JSONObject root = readRoot();
            if (!root.has(key.storageKey())) return null;
            return fromJson(root.getJSONObject(key.storageKey()));
        } catch (JSONException ignored) {
            return null;
        }
    }

    public synchronized BankEntry peekByEventId(String eventId) {
        if (eventId == null || eventId.isEmpty()) return null;
        try {
            JSONObject root = readRoot();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                BankEntry entry = fromJson(root.getJSONObject(keys.next()));
                if (eventId.equals(entry.eventId())) return entry;
            }
        } catch (JSONException ignored) {
            return null;
        }
        return null;
    }

    public synchronized List<BankEntry> all() {
        List<BankEntry> entries = new ArrayList<>();
        try {
            JSONObject root = readRoot();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                entries.add(fromJson(root.getJSONObject(keys.next())));
            }
        } catch (JSONException ignored) {
            return entries;
        }
        return entries;
    }

    public synchronized void clear(BankKey key) {
        if (key == null) return;
        try {
            JSONObject root = readRoot();
            root.remove(key.storageKey());
            preferences.edit().putString(ENTRIES, root.toString()).commit();
        } catch (JSONException ignored) {
            // Corrupt payload: leave as-is; next upsert rewrites.
        }
    }

    private JSONObject readRoot() throws JSONException {
        String raw = preferences.getString(ENTRIES, "{}");
        return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private static JSONObject toJson(BankEntry entry) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("peerKey", entry.key().peerKey());
        json.put("packageName", entry.key().packageName());
        json.put("eventId", entry.eventId());
        json.put("notificationKey", entry.notificationKey());
        json.put("senderName", entry.senderName());
        json.put("conversationLabel", entry.conversationLabel() == null
                ? JSONObject.NULL : entry.conversationLabel());
        json.put("body", entry.body() == null ? JSONObject.NULL : entry.body());
        json.put("updatedAt", entry.updatedAt());
        json.put("state", entry.state().name());
        json.put("snoozeUntilMillis", entry.snoozeUntilMillis());
        return json;
    }

    private static BankEntry fromJson(JSONObject json) throws JSONException {
        return new BankEntry(
                new BankKey(json.getString("peerKey"), json.getString("packageName")),
                json.getString("eventId"),
                json.optString("notificationKey", ""),
                json.optString("senderName", ""),
                json.isNull("conversationLabel") ? null : json.optString("conversationLabel", null),
                json.isNull("body") ? null : json.optString("body", null),
                json.optLong("updatedAt", 0L),
                BankState.fromStorage(json.optString("state", "EMPTY")),
                json.optLong("snoozeUntilMillis", 0L));
    }
}
