package com.textureflow.intelligence.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Summary returned to the UI. No action authority. */
public final class SummaryResult {
    private final String summary;
    private final double score;
    private final AttentionLevel level;
    private final String reason;
    private final IntelligenceIntent intent;
    private final boolean requiresResponse;
    private final List<String> ambiguities;
    private final AssessmentSource source;
    private final List<String> eventIds;
    private final boolean containedUntrustedInstructions;
    private final String modelFailure;

    public SummaryResult(
            String summary,
            double score,
            AttentionLevel level,
            String reason,
            IntelligenceIntent intent,
            boolean requiresResponse,
            List<String> ambiguities,
            AssessmentSource source,
            List<String> eventIds,
            boolean containedUntrustedInstructions,
            String modelFailure) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.score = score;
        this.level = Objects.requireNonNull(level, "level");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.requiresResponse = requiresResponse;
        this.ambiguities = copyList(ambiguities);
        this.source = Objects.requireNonNull(source, "source");
        this.eventIds = copyList(eventIds);
        this.containedUntrustedInstructions = containedUntrustedInstructions;
        this.modelFailure = modelFailure;
    }

    public String getSummary() { return summary; }
    public double getScore() { return score; }
    public AttentionLevel getLevel() { return level; }
    public String getReason() { return reason; }
    public IntelligenceIntent getIntent() { return intent; }
    public boolean requiresResponse() { return requiresResponse; }
    public List<String> getAmbiguities() { return ambiguities; }
    public AssessmentSource getSource() { return source; }
    public List<String> getEventIds() { return eventIds; }
    public boolean containedUntrustedInstructions() { return containedUntrustedInstructions; }
    public String getModelFailure() { return modelFailure; }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
