package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.policy.PolicyVerdict;
import com.textureflow.intelligence.triage.PriorityAssessment;

import java.util.Objects;

/** Structured triage output after SchemaValidator + PolicyGate (or deterministic fallback). */
public final class TriageRoleResult {
    public static final String PROMPT_VERSION = "triage.v1";

    private final IntelligenceIntent intent;
    private final boolean requiresResponse;
    private final AttentionLevel urgency;
    private final String reason;
    private final Double modelConfidence;
    private final AssessmentSource source;
    private final PolicyVerdict verdict;
    private final PriorityAssessment merged;
    private final boolean usedFallback;
    private final int inputTokens;
    private final int outputTokens;
    private final long wallMs;

    public TriageRoleResult(
            IntelligenceIntent intent,
            boolean requiresResponse,
            AttentionLevel urgency,
            String reason,
            Double modelConfidence,
            AssessmentSource source,
            PolicyVerdict verdict,
            PriorityAssessment merged,
            boolean usedFallback,
            int inputTokens,
            int outputTokens,
            long wallMs) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.requiresResponse = requiresResponse;
        this.urgency = Objects.requireNonNull(urgency, "urgency");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.modelConfidence = modelConfidence;
        this.source = Objects.requireNonNull(source, "source");
        this.verdict = verdict;
        this.merged = Objects.requireNonNull(merged, "merged");
        this.usedFallback = usedFallback;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.wallMs = wallMs;
    }

    public IntelligenceIntent getIntent() {
        return intent;
    }

    public boolean isRequiresResponse() {
        return requiresResponse;
    }

    public AttentionLevel getUrgency() {
        return urgency;
    }

    public String getReason() {
        return reason;
    }

    public Double getModelConfidence() {
        return modelConfidence;
    }

    public AssessmentSource getSource() {
        return source;
    }

    public PolicyVerdict getVerdict() {
        return verdict;
    }

    public PriorityAssessment getMerged() {
        return merged;
    }

    public boolean usedFallback() {
        return usedFallback;
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

    /** Triage never emits a draft. Injection may still classify. */
    public boolean hasDraft() {
        return false;
    }

    public String draftText() {
        return null;
    }
}
