package com.textureflow.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AttentionQueuePolicyTest {
    @Test
    public void ordinaryReplyCapableMessageIsActionable() {
        assertTrue(AttentionQueuePolicy.shouldSurface("NORMAL", true));
    }

    @Test
    public void importantNonReplyNotificationRemainsActionable() {
        assertTrue(AttentionQueuePolicy.shouldSurface("IMPORTANT", false));
    }

    @Test
    public void ordinaryNonActionableNotificationStaysOutOfMinimalHome() {
        assertFalse(AttentionQueuePolicy.shouldSurface("NORMAL", false));
    }
}
