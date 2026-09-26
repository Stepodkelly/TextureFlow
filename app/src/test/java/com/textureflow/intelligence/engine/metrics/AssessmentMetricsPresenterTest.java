package com.textureflow.intelligence.engine.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.ledger.AssessmentRecord;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AssessmentMetricsPresenterTest {
    @Test
    public void emptyRecordsShowEmptyLabel() {
        assertEquals(
                AssessmentMetricsPresenter.emptyLabel(),
                AssessmentMetricsPresenter.joined(AssessmentMetricsPresenter.lastLines(
                        Collections.emptyList(), 8)));
        assertEquals(
                AssessmentMetricsPresenter.emptyLabel(),
                AssessmentMetricsPresenter.joined(AssessmentMetricsPresenter.lastLines(null, 8)));
    }

    @Test
    public void lastLinesAreNewestFirstAndCapped() {
        List<AssessmentRecord> records = Arrays.asList(
                row("t1", AttentionLevel.LOW, "promo"),
                row("t2", AttentionLevel.NORMAL, "chat"),
                row("t3", AttentionLevel.IMPORTANT, "asked you to reply"),
                row("t4", AttentionLevel.URGENT, "time pressure"));
        List<String> lines = AssessmentMetricsPresenter.lastLines(records, 2);
        assertEquals(Arrays.asList(
                "URGENT · time pressure",
                "IMPORTANT · asked you to reply"), lines);
    }

    @Test
    public void linesExposeOnlyLevelAndReasonText() {
        AssessmentRecord row = new AssessmentRecord(
                "tick-secret",
                AttentionLevel.IMPORTANT,
                0.91,
                0.44,
                "direct_request",
                "Sam asked you to come down.");
        String line = AssessmentMetricsPresenter.line(row);
        assertEquals("IMPORTANT · Sam asked you to come down.", line);
        assertFalse(line.contains("tick-secret"));
        assertFalse(line.contains("0.91"));
        assertFalse(line.contains("direct_request"));
    }

    @Test
    public void reasonOnlyWhenLevelMissingIsStillSafe() {
        AssessmentRecord row = row("t1", AttentionLevel.LOW, "");
        assertEquals("LOW", AssessmentMetricsPresenter.line(row));
    }

    @Test
    public void joinedKeepsLevelAndReasonOnly() {
        String text = AssessmentMetricsPresenter.joined(AssessmentMetricsPresenter.lastLines(
                Collections.singletonList(row("t1", AttentionLevel.NORMAL, "hello")), 8));
        assertTrue(text.contains("NORMAL · hello"));
        assertFalse(text.contains("t1"));
    }

    private static AssessmentRecord row(String tickId, AttentionLevel level, String reason) {
        return new AssessmentRecord(tickId, level, 0.5, null, "code", reason);
    }
}
