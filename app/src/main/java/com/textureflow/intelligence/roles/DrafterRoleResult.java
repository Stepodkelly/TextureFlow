package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.policy.PolicyVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Structured draft after SchemaValidator + PolicyGate, or the user's literal words. */
public final class DrafterRoleResult {
    public static final String PROMPT_VERSION = "draft.v1";

    private final String replyText;
    private final ReplyTone tone;
    private final double confidence;
    private final List<String> ambiguities;
    private final AssessmentSource source;
    private final PolicyVerdict verdict;
    private final boolean usedFallback;
    private final boolean usedLiteral;
    private final boolean schemaValid;
    private final int inputTokens;
    private final int outputTokens;
    private final long wallMs;

    public DrafterRoleResult(
            String replyText,
            ReplyTone tone,
            double confidence,
            List<String> ambiguities,
            AssessmentSource source,
            PolicyVerdict verdict,
            boolean usedFallback,
            boolean usedLiteral,
            boolean schemaValid,
            int inputTokens,
            int outputTokens,
            long wallMs) {
        this.replyText = replyText == null ? "" : replyText;
        this.tone = tone;
        this.confidence = confidence;
        this.ambiguities = copyList(ambiguities);
        this.source = Objects.requireNonNull(source, "source");
        this.verdict = verdict;
        this.usedFallback = usedFallback;
        this.usedLiteral = usedLiteral;
        this.schemaValid = schemaValid;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.wallMs = wallMs;
    }

    public String getReplyText() {
        return replyText;
    }

    public ReplyTone getTone() {
        return tone;
    }

    public double getConfidence() {
        return confidence;
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

    public boolean usedLiteral() {
        return usedLiteral;
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
