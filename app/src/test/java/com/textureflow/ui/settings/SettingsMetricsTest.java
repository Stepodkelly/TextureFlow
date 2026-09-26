package com.textureflow.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.engine.metrics.AssessmentMetricsPresenter;
import com.textureflow.intelligence.engine.metrics.RoleRunMetricsPresenter;
import com.textureflow.intelligence.ledger.AssessmentRecord;
import com.textureflow.intelligence.ledger.CouncilRole;
import com.textureflow.intelligence.ledger.InMemoryLedger;
import com.textureflow.intelligence.ledger.RoleRunRecord;

import org.junit.Test;

import java.util.List;

public final class SettingsMetricsTest {
    @Test
    public void nullLedgerIsEmpty() {
        assertTrue(SettingsMetrics.inspectAssessments(null).isEmpty());
        assertEquals(AssessmentMetricsPresenter.emptyLabel(), SettingsMetrics.summary(null));
    }

    @Test
    public void unknownLedgerWithoutInspectorIsEmpty() {
        assertTrue(SettingsMetrics.inspectAssessments(new Object()).isEmpty());
    }

    @Test
    public void inspectorReadsInMemoryLedgerAssessments() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.recordAssessment(new AssessmentRecord(
                "tick-a", AttentionLevel.LOW, 0.2, null, "promo", "promotion"));
        ledger.recordAssessment(new AssessmentRecord(
                "tick-b", AttentionLevel.URGENT, 0.8, null, "time", "time pressure"));

        List<AssessmentRecord> rows = SettingsMetrics.inspectAssessments(ledger);
        assertEquals(2, rows.size());
        String summary = SettingsMetrics.summary(ledger);
        assertTrue(summary.contains("URGENT · time pressure"));
        assertTrue(summary.contains("LOW · promotion"));
        assertFalse(summary.contains("tick-a"));
        assertFalse(summary.contains("0.8"));
    }

    @Test
    public void summaryIncludesRoleRunsWithoutBodies() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.recordRoleRun(new RoleRunRecord(
                "tick-s", CouncilRole.SUMMARIZER, "summary.v1", 40, 12, 88, true, List.of()));
        String summary = SettingsMetrics.summary(ledger);
        assertTrue(summary.contains(RoleRunMetricsPresenter.heading()));
        assertTrue(summary.contains("SUMMARIZER · 88 ms · ok"));
        assertFalse(summary.contains("Dinner"));
    }

    @Test
    public void preferenceKeysStayOnExistingStore() {
        assertEquals("texture_surface_preferences", IntelligencePreferences.PREFERENCES);
        assertEquals("intelligence_mode", IntelligencePreferences.KEY_MODE);
        assertTrue(IntelligencePreferences.DEFAULT_MODE);
    }
}
