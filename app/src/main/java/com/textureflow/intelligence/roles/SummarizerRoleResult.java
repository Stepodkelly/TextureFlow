package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.policy.PolicyVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Structured summary after SchemaValidator + PolicyGate (or deterministic fallback). */
public final class SummarizerRoleResult {
    public static final String PROMPT_VERSION = "summary.v1";

    private final String summary;
    private final double score;
    private final AttentionLevel level;
    private final String reason;
    private final IntelligenceIntent intent;
    private final boolean requiresResponse;
    private final List<String> ambiguities;
    private final AssessmentSource source;
    private final PolicyVerdict verdict;
    private final boolean usedFallback;
    private final boolean schemaValid;
    private final int inputTokens;
    private final int outputTokens;
    private final long wallMs;

    public SummarizerRoleResult(
            String summary,
            double score,
            AttentionLevel level,
            String reason,
            IntelligenceIntent intent,
            boolean requiresResponse,
            List<String> ambiguities,
            AssessmentSource source,
            PolicyVerdict verdict,
            boolean usedFallback,
            boolean schemaValid,
            int inputTokens,
            int outputTokens,
            long wallMs) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.score = score;
        this.level = Objects.requireNonNull(level, "level");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.requiresResponse = requiresResponse;
        this.ambiguities = copyList(ambiguities);
        this.source = Objects.requireNonNull(source, "source");
        this.verdict = verdict;
        this.usedFallback = usedFallback;
        this.schemaValid = schemaValid;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.wallMs = wallMs;
    }

    public String getSummary() {
        return summary;
    }

    public double getScore() {
        return score;
    }

    public AttentionLevel getLevel() {
        return level;
    }

    public String getReason() {
        return reason;
    }

    public IntelligenceIntent getIntent() {
        return intent;
    }

    public boolean isRequiresResponse() {
        return requiresResponse;
    }

    public List<String> getAmbiguities() {
        return ambiguities;
    }

    public AssessmentSource getSource() {
        return source;
    }

    public PolicyVerdict getVerdict() {
        return verdict;
    }

    public boolean usedFallback() {
        return usedFallback;
    }

    public boolean isSchemaValid() {
        return schemaValid;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public long getWallMs() {
        return wallMs;
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
