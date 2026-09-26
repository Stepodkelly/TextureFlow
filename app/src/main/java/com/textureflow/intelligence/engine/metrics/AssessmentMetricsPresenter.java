package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.ledger.AssessmentRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Formats ledger assessments for Settings. Only {@code level} and {@code reason_text}
 * are shown — never message bodies or confidence scores.
 */
public final class AssessmentMetricsPresenter {
    public static final int DEFAULT_LIMIT = 8;

    private AssessmentMetricsPresenter() {}

    public static List<String> lastLines(List<AssessmentRecord> records, int limit) {
        if (records == null || records.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        int from = Math.max(0, records.size() - limit);
        List<String> lines = new ArrayList<>();
        for (int i = records.size() - 1; i >= from; i--) {
            AssessmentRecord row = records.get(i);
            if (row == null) {
                continue;
            }
            lines.add(line(row));
        }
        return Collections.unmodifiableList(lines);
    }

    public static String line(AssessmentRecord row) {
        String level = row.getLevel() == null ? "" : row.getLevel().name();
        String reason = row.getReasonText() == null ? "" : row.getReasonText();
        if (reason.isEmpty()) {
            return level;
        }
        if (level.isEmpty()) {
            return reason;
        }
        return level + " · " + reason;
    }

    public static String emptyLabel() {
        return "No assessments recorded yet.";
    }

    public static String joined(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return emptyLabel();
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines.get(i));
        }
        return out.toString();
    }
}
