package com.textureflow.connection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

final class ConnectionIds {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConnectionIds() {}

    static String newClaimToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    static String stableTraceId(String operationKey) {
        return "trace_android_" + sha256(operationKey).substring(0, 24);
    }

    static String storageKey(String commandId) {
        return "claim_" + sha256(commandId);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        char[] encoded = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            encoded[index * 2] = alphabet[value >>> 4];
            encoded[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(encoded);
    }
}
