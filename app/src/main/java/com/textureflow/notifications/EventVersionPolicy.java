package com.textureflow.notifications;

public final class EventVersionPolicy {
    private EventVersionPolicy() {}

    public static int nextVersion(int previousVersion, String previousStatus, String previousHash, String incomingHash) {
        if (previousVersion < 1) {
            return 1;
        }
        if ("REMOVED".equals(previousStatus) || !safeEquals(previousHash, incomingHash)) {
            return previousVersion + 1;
        }
        return previousVersion;
    }

    public static String nextStatus(int previousVersion, String previousStatus, String previousHash, String incomingHash) {
        if (previousVersion < 1) {
            return "ACTIVE";
        }
        if ("REMOVED".equals(previousStatus) || !safeEquals(previousHash, incomingHash)) {
            return "UPDATED";
        }
        return previousStatus;
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
