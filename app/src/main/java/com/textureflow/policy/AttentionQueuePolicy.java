package com.textureflow.policy;

import com.textureflow.intelligence.api.AttentionAssessment;

/** Decides which live notifications belong on the minimal actionable Home queue. */
public final class AttentionQueuePolicy {
    private AttentionQueuePolicy() {}

    public static boolean shouldSurface(AttentionAssessment assessment, boolean replyCapable) {
        if (assessment == null) {
            return false;
        }
        return shouldSurface(assessment.getLevel().name(), replyCapable);
    }

    public static boolean shouldSurface(String priorityLevel, boolean replyCapable) {
        return replyCapable
                || "IMPORTANT".equals(priorityLevel)
                || "URGENT".equals(priorityLevel);
    }
}
