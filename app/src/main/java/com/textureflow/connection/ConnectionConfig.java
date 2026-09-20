package com.textureflow.connection;

import java.net.URI;

public record ConnectionConfig(
        ConnectionMode mode,
        String convexUrl,
        String ownerId,
        String deviceId,
        String deviceActorToken,
        String oidcToken,
        String deviceDisplayName,
        String appVersion,
        long heartbeatIntervalMs,
        long healthyPollIntervalMs,
        long watchdogStaleAfterMs) {

    public static final String STUB_URL = "stub://local";

    public ConnectionConfig {
        mode = mode == null ? ConnectionMode.STUB : mode;
        ownerId = required(ownerId, "Owner ID");
        deviceId = required(deviceId, "Device ID");
        deviceDisplayName = required(deviceDisplayName, "Device display name");
        appVersion = required(appVersion, "App version");
        if (mode == ConnectionMode.STUB) {
            convexUrl = STUB_URL;
            if (blank(deviceActorToken)) deviceActorToken = "stub-device-token";
            if (blank(oidcToken)) oidcToken = "";
        } else {
            convexUrl = required(convexUrl, "Convex URL");
            if (blank(deviceActorToken) && blank(oidcToken)) {
                throw new IllegalArgumentException("A device actor token or OIDC token is required");
            }
            URI uri = URI.create(convexUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Convex URL must be an HTTPS deployment URL");
            }
            if ((uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Convex URL must not contain a path, query, or fragment");
            }
            while (convexUrl.endsWith("/")) convexUrl = convexUrl.substring(0, convexUrl.length() - 1);
        }
        if (heartbeatIntervalMs < 5_000L || heartbeatIntervalMs > 30_000L) {
            throw new IllegalArgumentException("Heartbeat interval must be between 5 and 30 seconds");
        }
        if (healthyPollIntervalMs < 250L || healthyPollIntervalMs > 10_000L) {
            throw new IllegalArgumentException("Poll interval must be between 250 ms and 10 seconds");
        }
        if (watchdogStaleAfterMs < 10_000L || watchdogStaleAfterMs < healthyPollIntervalMs * 2) {
            throw new IllegalArgumentException("Watchdog deadline is too short");
        }
    }

    public boolean isStub() {
        return mode == ConnectionMode.STUB;
    }

    public static ConnectionConfig localStub(
            String ownerId,
            String deviceId,
            String deviceDisplayName,
            String appVersion) {
        return new ConnectionConfig(
                ConnectionMode.STUB,
                STUB_URL,
                ownerId,
                deviceId,
                "stub-device-token",
                "",
                deviceDisplayName,
                appVersion,
                25_000L,
                1_500L,
                20_000L);
    }

    public static ConnectionConfig liveDefaults(
            String convexUrl,
            String ownerId,
            String deviceId,
            String deviceActorToken,
            String oidcToken,
            String deviceDisplayName,
            String appVersion) {
        return new ConnectionConfig(
                ConnectionMode.LIVE,
                convexUrl, ownerId, deviceId, deviceActorToken, oidcToken,
                deviceDisplayName, appVersion, 25_000L, 1_500L, 20_000L);
    }

    /** @deprecated Prefer {@link #liveDefaults} or {@link #localStub}. */
    @Deprecated
    public static ConnectionConfig defaults(
            String convexUrl,
            String ownerId,
            String deviceId,
            String deviceActorToken,
            String oidcToken,
            String deviceDisplayName,
            String appVersion) {
        if (blank(convexUrl) || STUB_URL.equals(convexUrl) || convexUrl.startsWith("stub:")) {
            return localStub(ownerId, deviceId, deviceDisplayName, appVersion);
        }
        return liveDefaults(
                convexUrl, ownerId, deviceId, deviceActorToken, oidcToken, deviceDisplayName, appVersion);
    }

    private static String required(String value, String label) {
        if (blank(value)) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
