package com.textureflow.policy;

import java.time.DateTimeException;
import java.time.Instant;

public final class CommandFreshness {
    private CommandFreshness() {}

    public static boolean isExpired(String expiresAt, long nowEpochMs) {
        if (expiresAt == null || expiresAt.trim().isEmpty()) return true;
        try {
            return !Instant.parse(expiresAt).isAfter(Instant.ofEpochMilli(nowEpochMs));
        } catch (DateTimeException invalidTimestamp) {
            return true;
        }
    }
}
