package com.textureflow.bank;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stub refill coordinator.
 *
 * <p>Records that a branded handshake refill was requested for a {@link BankKey}.
 * Does <strong>not</strong> send network messages or touch messengers — only the
 * local state machine / prefs API until a real peer-presence path is wired.
 */
public final class HandshakeRefill {
    private static final String PREFS = "textureflow-handshake-refill";
    private static final String PENDING = "pending";

    private final SharedPreferences preferences;

    public HandshakeRefill(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    /** Test / injection constructor. */
    public HandshakeRefill(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is required");
        this.preferences = preferences;
    }

    /** Record a refill request for this bank key (no network). */
    public synchronized void request(BankKey key) {
        if (key == null) throw new IllegalArgumentException("key is required");
        try {
            JSONObject root = readRoot();
            root.put(key.storageKey(), toJson(key, System.currentTimeMillis()));
            persist(root);
        } catch (JSONException failure) {
            throw new IllegalStateException("Could not record refill request", failure);
        }
    }

    public synchronized boolean isPending(BankKey key) {
        if (key == null) return false;
        try {
            return readRoot().has(key.storageKey());
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized void clear(BankKey key) {
        if (key == null) return;
        try {
            JSONObject root = readRoot();
            root.remove(key.storageKey());
            persist(root);
        } catch (JSONException ignored) {
            // Corrupt payload: leave as-is.
        }
    }

    /** Snapshot of pending refill keys (stub inspection / tests). */
    public synchronized List<BankKey> pendingKeys() {
        try {
            JSONObject root = readRoot();
            List<BankKey> keys = new ArrayList<>();
            JSONArray names = root.names();
            if (names == null) return Collections.emptyList();
            for (int i = 0; i < names.length(); i++) {
                JSONObject row = root.getJSONObject(names.getString(i));
                keys.add(new BankKey(row.getString("peerKey"), row.getString("packageName")));
            }
            return keys;
        } catch (JSONException | IllegalArgumentException ignored) {
            return Collections.emptyList();
        }
    }

    private void persist(JSONObject root) {
        if (!preferences.edit().putString(PENDING, root.toString()).commit()) {
            throw new IllegalStateException("Could not persist refill stub state");
        }
    }

    private JSONObject readRoot() throws JSONException {
        String raw = preferences.getString(PENDING, "{}");
        return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private static JSONObject toJson(BankKey key, long requestedAt) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("peerKey", key.peerKey());
        json.put("packageName", key.packageName());
        json.put("requestedAt", requestedAt);
        return json;
    }
}
