package com.textureflow.bank;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Durable local queue of outbound texts keyed by peer × package, held until a
 * live REPLY handle exists. Separate from the Convex device outbox.
 */
public final class OutboundMessageOutbox {
    private static final String PREFS = "textureflow-outbound-message-outbox";
    private static final String MESSAGES = "messages";
    static final int MAX_QUEUE_SIZE = 200;

    private final SharedPreferences preferences;

    public OutboundMessageOutbox(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    /** Test / injection constructor. */
    public OutboundMessageOutbox(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is required");
        this.preferences = preferences;
    }

    public synchronized String enqueue(String peerKey, String packageName, String body) {
        if (peerKey == null || peerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("peerKey is required");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName is required");
        }
        if (body == null) throw new IllegalArgumentException("body is required");
        try {
            JSONArray root = readRoot();
            while (root.length() >= MAX_QUEUE_SIZE) {
                root.remove(0);
            }
            String id = UUID.randomUUID().toString();
            OutboundMessage message = new OutboundMessage(
                    id, peerKey.trim(), packageName.trim(), body,
                    System.currentTimeMillis(), 0, null);
            root.put(toJson(message));
            persist(root);
            return id;
        } catch (JSONException failure) {
            throw new IllegalStateException("Could not enqueue outbound message", failure);
        }
    }

    /** Oldest-first ready messages, optionally capped. */
    public synchronized List<OutboundMessage> peekReady(int limit) {
        int capped = Math.max(0, limit);
        List<OutboundMessage> ready = new ArrayList<>();
        try {
            JSONArray root = readRoot();
            for (int i = 0; i < root.length() && ready.size() < capped; i++) {
                OutboundMessage message = fromJson(root.getJSONObject(i));
                ready.add(message);
            }
        } catch (JSONException ignored) {
            return ready;
        }
        return ready;
    }

    public synchronized void markSent(String id) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray root = readRoot();
            JSONArray next = new JSONArray();
            for (int i = 0; i < root.length(); i++) {
                JSONObject row = root.getJSONObject(i);
                if (!id.equals(row.optString("id", null))) {
                    next.put(row);
                }
            }
            persist(next);
        } catch (JSONException ignored) {
            // Corrupt payload: leave as-is.
        }
    }

    /** Increment attempts, store lastError, keep in queue for retry. */
    public synchronized void markFailed(String id, String error) {
        if (id == null || id.isEmpty()) return;
        try {
            JSONArray root = readRoot();
            for (int i = 0; i < root.length(); i++) {
                JSONObject row = root.getJSONObject(i);
                if (id.equals(row.optString("id", null))) {
                    OutboundMessage updated = fromJson(row).withFailure(error);
                    root.put(i, toJson(updated));
                    persist(root);
                    return;
                }
            }
        } catch (JSONException ignored) {
            // Corrupt payload: leave as-is.
        }
    }

    public synchronized void clearForKey(BankKey key) {
        if (key == null) return;
        try {
            JSONArray root = readRoot();
            JSONArray next = new JSONArray();
            for (int i = 0; i < root.length(); i++) {
                OutboundMessage message = fromJson(root.getJSONObject(i));
                if (!key.equals(message.bankKey())) {
                    next.put(root.getJSONObject(i));
                }
            }
            persist(next);
        } catch (JSONException ignored) {
            // Corrupt payload: leave as-is.
        }
    }

    public synchronized int size() {
        try {
            return readRoot().length();
        } catch (JSONException ignored) {
            return 0;
        }
    }

    public synchronized boolean hasPendingFor(BankKey key) {
        if (key == null) return false;
        try {
            JSONArray root = readRoot();
            for (int i = 0; i < root.length(); i++) {
                OutboundMessage message = fromJson(root.getJSONObject(i));
                if (key.equals(message.bankKey())) return true;
            }
        } catch (JSONException ignored) {
            return false;
        }
        return false;
    }

    private void persist(JSONArray root) {
        if (!preferences.edit().putString(MESSAGES, root.toString()).commit()) {
            throw new IllegalStateException("Could not persist outbound outbox");
        }
    }

    private JSONArray readRoot() throws JSONException {
        String raw = preferences.getString(MESSAGES, "[]");
        return raw == null || raw.isEmpty() ? new JSONArray() : new JSONArray(raw);
    }

    private static JSONObject toJson(OutboundMessage message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", message.id());
        json.put("peerKey", message.peerKey());
        json.put("packageName", message.packageName());
        json.put("body", message.body());
        json.put("createdAt", message.createdAt());
        json.put("attempts", message.attempts());
        json.put("lastError", message.lastError() == null ? JSONObject.NULL : message.lastError());
        return json;
    }

    private static OutboundMessage fromJson(JSONObject json) throws JSONException {
        return new OutboundMessage(
                json.getString("id"),
                json.getString("peerKey"),
                json.getString("packageName"),
                json.getString("body"),
                json.optLong("createdAt", 0L),
                json.optInt("attempts", 0),
                json.isNull("lastError") ? null : json.optString("lastError", null));
    }
}
