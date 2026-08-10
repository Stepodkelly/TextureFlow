package com.textureflow.connection;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Runtime values override optional BuildConfig fields; no credential is defined in source. */
public final class ConnectionConfigStore {
    private static final String PREFERENCES = "textureflow-connection-config";
    private static final String URL = "convex-url";
    private static final String OWNER = "owner-id";
    private static final String DEVICE_TOKEN = "device-actor-token";
    private static final String OIDC_TOKEN = "oidc-token";
    private static final String USER_ACTION_TOKEN = "user-action-token";
    private static final String DISPLAY_NAME = "device-display-name";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "textureflow.connection.credentials.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private ConnectionConfigStore() {}

    public static void saveRuntime(
            Context context,
            String convexUrl,
            String ownerId,
            String deviceActorToken,
            String oidcToken,
            String deviceDisplayName) {
        SharedPreferences.Editor editor = preferences(context).edit()
                .putString(URL, convexUrl)
                .putString(OWNER, ownerId)
                .putString(DISPLAY_NAME, deviceDisplayName);
        putEncryptedOrRemove(editor, DEVICE_TOKEN, deviceActorToken);
        putEncryptedOrRemove(editor, OIDC_TOKEN, oidcToken);
        if (!editor.commit()) throw new IllegalStateException("Could not persist connection configuration");
    }

    public static ConnectionConfig load(Context context, String deviceId) {
        SharedPreferences values = preferences(context);
        String url = first(values.getString(URL, null), buildField("CONVEX_URL"));
        String owner = first(values.getString(OWNER, null), buildField("TEXTUREFLOW_OWNER_ID"));
        String deviceToken = first(
                decrypt(values.getString(DEVICE_TOKEN, null)), buildField("TEXTUREFLOW_DEVICE_TOKEN"));
        String oidcToken = first(
                decrypt(values.getString(OIDC_TOKEN, null)), buildField("TEXTUREFLOW_OIDC_TOKEN"));
        String displayName = first(
                values.getString(DISPLAY_NAME, null), buildField("TEXTUREFLOW_DEVICE_NAME"));
        if (blank(displayName)) displayName = Build.MANUFACTURER + " " + Build.MODEL;
        String version = first(buildField("VERSION_NAME"), "0.1.0");
        return ConnectionConfig.defaults(
                url, owner, deviceId, deviceToken, oidcToken, displayName.trim(), version);
    }

    public static void clearSecrets(Context context) {
        if (!preferences(context).edit().remove(DEVICE_TOKEN).remove(OIDC_TOKEN)
                .remove(USER_ACTION_TOKEN).commit()) {
            throw new IllegalStateException("Could not clear connection credentials");
        }
    }

    public static void saveUserActionToken(Context context, String token) {
        SharedPreferences.Editor editor = preferences(context).edit();
        putEncryptedOrRemove(editor, USER_ACTION_TOKEN, token);
        if (!editor.commit()) throw new IllegalStateException("Could not persist User action credential");
    }

    public static String loadUserActionToken(Context context) {
        return decrypt(preferences(context).getString(USER_ACTION_TOKEN, null));
    }

    public static boolean isConfigured(Context context, String deviceId) {
        try {
            load(context, deviceId);
            return true;
        } catch (RuntimeException invalidOrMissing) {
            return false;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static void putEncryptedOrRemove(
            SharedPreferences.Editor editor, String key, String value) {
        if (blank(value)) {
            editor.remove(key);
            return;
        }
        editor.putString(key, encrypt(value));
    }

    private static String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, credentialKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not protect connection credentials", failure);
        }
    }

    private static String decrypt(String value) {
        if (blank(value)) return null;
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) return null;
        try {
            byte[] iv = Base64.decode(value.substring(0, separator), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(value.substring(separator + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, credentialKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return null;
        }
    }

    private static SecretKey credentialKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
        } catch (java.io.IOException impossible) {
            throw new GeneralSecurityException("Could not open Android Keystore", impossible);
        }
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey secretKey) return secretKey;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String buildField(String name) {
        try {
            Class<?> buildConfig = Class.forName("com.textureflow.BuildConfig");
            Field field = buildConfig.getField(name);
            Object value = field.get(null);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String first(String primary, String fallback) {
        return blank(primary) ? fallback : primary;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
