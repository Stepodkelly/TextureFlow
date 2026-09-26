package com.textureflow.ui.settings;

import com.textureflow.intelligence.engine.metrics.AssessmentMetricsPresenter;
import com.textureflow.intelligence.ledger.AssessmentRecord;
import com.textureflow.intelligence.ledger.SqliteLedger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads last-N assessment lines from {@code runtime.ledger()} without touching SQLite
 * from a pure presenter. Uses {@code instanceof SqliteLedger} plus {@code getAssessments()}
 * inspectors (reflection fallback for JVM fakes).
 */
public final class SettingsMetrics {
    private SettingsMetrics() {}

    public static String summary(Object ledger) {
        return AssessmentMetricsPresenter.joined(
                AssessmentMetricsPresenter.lastLines(
                        inspectAssessments(ledger), AssessmentMetricsPresenter.DEFAULT_LIMIT));
    }

    @SuppressWarnings("unchecked")
    public static List<AssessmentRecord> inspectAssessments(Object ledger) {
        if (ledger == null) {
            return Collections.emptyList();
        }
        if (ledger instanceof SqliteLedger) {
            List<AssessmentRecord> rows = ((SqliteLedger) ledger).getAssessments();
            return rows == null ? Collections.emptyList() : rows;
        }
        try {
            Method inspector = ledger.getClass().getMethod("getAssessments");
            Object value = inspector.invoke(ledger);
            if (!(value instanceof List<?>)) {
                return Collections.emptyList();
            }
            List<AssessmentRecord> rows = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof AssessmentRecord) {
                    rows.add((AssessmentRecord) item);
                }
            }
            return rows;
        } catch (ReflectiveOperationException ignored) {
            return Collections.emptyList();
        }
    }
}
