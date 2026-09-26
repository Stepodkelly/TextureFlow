package com.textureflow.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;

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

    @Test
    public void assessmentOverloadMatchesStringOverload() {
        AttentionAssessment important = assessment(AttentionLevel.IMPORTANT);
        AttentionAssessment normal = assessment(AttentionLevel.NORMAL);
        assertTrue(AttentionQueuePolicy.shouldSurface(important, false));
        assertFalse(AttentionQueuePolicy.shouldSurface(normal, false));
        assertTrue(AttentionQueuePolicy.shouldSurface(normal, true));
        assertFalse(AttentionQueuePolicy.shouldSurface((AttentionAssessment) null, true));
    }

    private static AttentionAssessment assessment(AttentionLevel level) {
        return new AttentionAssessment(
                "tick_1", "person_sam", "com.whatsapp", "evt_1", 1, 0.7,
                level, "reason", 0.7, null, AssessmentSource.DETERMINISTIC);
    }
}
