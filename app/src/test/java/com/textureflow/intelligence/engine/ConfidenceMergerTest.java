package com.textureflow.intelligence.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.triage.PriorityAssessment;
import com.textureflow.intelligence.triage.PriorityFeatures;

import org.junit.Test;

public final class ConfidenceMergerTest {
    @Test
    public void onlySystemConfidenceCanReachUrgent() {
        PriorityAssessment deterministic = new PriorityAssessment(
                0.45, AttentionLevel.NORMAL, "no strong signal");
        PriorityFeatures features = new PriorityFeatures(0.5, 0, 0, 1, 0.5, false, false);
        ConfidenceMerger.Result result = ConfidenceMerger.merge(
                deterministic, features, AttentionLevel.URGENT, 0.99, true);
        assertEquals(AttentionLevel.IMPORTANT, result.getLevel());
        assertTrue(result.getSystemConfidence() < ConfidenceMerger.URGENT_SYSTEM_THRESHOLD);
    }

    @Test
    public void modelUrgentWithoutUrgencySignalsCapsToImportant() {
        PriorityAssessment deterministic = new PriorityAssessment(
                0.85, AttentionLevel.URGENT, "important contact");
        PriorityFeatures features = new PriorityFeatures(1.0, 0, 0, 1, 0.9, false, false);
        ConfidenceMerger.Result result = ConfidenceMerger.merge(
                deterministic, features, AttentionLevel.URGENT, 0.99, true);
        assertEquals(AttentionLevel.IMPORTANT, result.getLevel());
    }

    @Test
    public void modelUrgentWithUrgencySignalsStaysUrgentWhenSystemAgrees() {
        PriorityAssessment deterministic = new PriorityAssessment(
                0.95, AttentionLevel.URGENT, "urgent request");
        PriorityFeatures features = new PriorityFeatures(1.0, 1.0, 0.8, 1, 0.9, false, false);
        ConfidenceMerger.Result result = ConfidenceMerger.merge(
                deterministic, features, AttentionLevel.URGENT, 0.8, true);
        assertEquals(AttentionLevel.URGENT, result.getLevel());
        assertTrue(result.getSystemConfidence() >= ConfidenceMerger.URGENT_SYSTEM_THRESHOLD);
    }

    @Test
    public void rejectedModelLeavesDeterministicLevel() {
        PriorityAssessment deterministic = new PriorityAssessment(
                0.72, AttentionLevel.IMPORTANT, "direct request");
        PriorityFeatures features = new PriorityFeatures(0.85, 0, 1.0, 1, 0.9, false, false);
        ConfidenceMerger.Result result = ConfidenceMerger.merge(
                deterministic, features, AttentionLevel.URGENT, 0.99, false);
        assertEquals(AttentionLevel.IMPORTANT, result.getLevel());
        assertEquals(0.72, result.getSystemConfidence(), 0.0);
    }

    @Test
    public void modelAgreementSlightlyRaisesSystemConfidence() {
        PriorityAssessment deterministic = new PriorityAssessment(
                0.50, AttentionLevel.NORMAL, "recent");
        PriorityFeatures features = new PriorityFeatures(0.5, 0, 0, 1, 0.5, false, false);
        ConfidenceMerger.Result result = ConfidenceMerger.merge(
                deterministic, features, AttentionLevel.NORMAL, 0.4, true);
        assertEquals(0.55, result.getSystemConfidence(), 0.0001);
        assertEquals(AttentionLevel.NORMAL, result.getLevel());
    }
}
