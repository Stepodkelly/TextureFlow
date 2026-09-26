package com.textureflow.intelligence.engine.metrics;

import com.textureflow.intelligence.ledger.RoleRunRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Last-N role-run lines for Settings. Metrics only — no prompts or bodies. */
public final class RoleRunMetricsPresenter {
    public static final int DEFAULT_LIMIT = 6;

    private RoleRunMetricsPresenter() {}

    public static List<String> lastLines(List<RoleRunRecord> records, int limit) {
        if (records == null || records.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        int from = Math.max(0, records.size() - limit);
        List<String> lines = new ArrayList<>();
        for (int i = records.size() - 1; i >= from; i--) {
            RoleRunRecord row = records.get(i);
            if (row == null) {
                continue;
            }
            lines.add(line(row));
        }
        return Collections.unmodifiableList(lines);
    }

    public static String line(RoleRunRecord row) {
        String role = row.getRole() == null ? "" : row.getRole().name();
        String valid = row.isSchemaValid() ? "ok" : "fallback";
        return role + " · " + row.getWallMs() + " ms · " + valid;
    }

    public static String heading() {
        return "Role runs";
    }

    public static String joined(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(heading());
        for (String line : lines) {
            out.append('\n').append(line);
        }
        return out.toString();
    }
}
