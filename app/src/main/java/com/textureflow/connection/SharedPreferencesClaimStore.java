package com.textureflow.connection;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Persists claim ownership before the first claim request so process death cannot change tokens. */
public final class SharedPreferencesClaimStore implements ClaimStore {
    private static final String PREFERENCES = "textureflow-command-claims";
    private static final String VERSION = "v1";

    private final SharedPreferences preferences;

    public SharedPreferencesClaimStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override
    public synchronized ClaimRecord find(String commandId) {
        String value = preferences.getString(ConnectionIds.storageKey(commandId), null);
        if (value == null) return null;
        ClaimRecord record = decode(value);
        if (!commandId.equals(record.commandId())) {
            throw new SecurityException("Persisted command claim identity does not match its key");
        }
        return record;
    }

    @Override
    public synchronized ClaimRecord getOrCreate(RemoteCommand command, long nowMillis) {
        ClaimRecord existing = find(command.commandId());
        if (existing != null) {
            if (!existing.traceId().equals(command.traceId())
                    || !existing.proposalId().equals(command.proposalId())) {
                throw new SecurityException("Persisted command claim conflicts with the server command");
            }
            return existing;
        }
        ClaimRecord created = new ClaimRecord(
                command.commandId(), ConnectionIds.newClaimToken(), command.traceId(),
                command.proposalId(), nowMillis);
        boolean saved = preferences.edit()
                .putString(ConnectionIds.storageKey(command.commandId()), encode(created))
                .commit();
        if (!saved) throw new IllegalStateException("Could not persist command claim before claiming");
        return created;
    }

    @Override
    public synchronized void remove(String commandId) {
        if (!preferences.edit().remove(ConnectionIds.storageKey(commandId)).commit()) {
            throw new IllegalStateException("Could not remove completed command claim");
        }
    }

    private static String encode(ClaimRecord record) {
        return String.join("\n",
                VERSION,
                base64(record.commandId()),
                base64(record.claimToken()),
                base64(record.traceId()),
                base64(record.proposalId()),
                Long.toString(record.createdAtMillis()));
    }

    private static ClaimRecord decode(String encoded) {
        try {
            String[] parts = encoded.split("\\n", -1);
            if (parts.length != 6 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported persisted claim format");
            }
            return new ClaimRecord(
                    unbase64(parts[1]), unbase64(parts[2]), unbase64(parts[3]),
                    unbase64(parts[4]), Long.parseLong(parts[5]));
        } catch (RuntimeException invalid) {
            throw new SecurityException("Persisted command claim is invalid", invalid);
        }
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
