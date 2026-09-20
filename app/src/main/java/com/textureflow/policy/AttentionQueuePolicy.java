package com.textureflow.policy;

/** Decides which live notifications belong on the minimal actionable Home queue. */
public final class AttentionQueuePolicy {
    private AttentionQueuePolicy() {}

    public static boolean shouldSurface(String priorityLevel, boolean replyCapable) {
        return replyCapable
                || "IMPORTANT".equals(priorityLevel)
                || "URGENT".equals(priorityLevel);
    }
}
