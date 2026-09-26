package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.policy.PolicyGate;
import com.textureflow.intelligence.policy.PolicyRequest;
import com.textureflow.intelligence.policy.PolicyVerdict;
import com.textureflow.intelligence.policy.SchemaName;
import com.textureflow.intelligence.policy.SchemaValidationException;
import com.textureflow.intelligence.policy.SchemaValidator;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.ModelPriorityHint;
import com.textureflow.intelligence.triage.PriorityAssessment;
import com.textureflow.intelligence.triage.PriorityResult;

import org.json.JSONObject;

import java.util.Objects;

/**
 * Single structured Triage call (doc §6.1). Output goes through {@link SchemaValidator}
 * then {@link PolicyGate}. Any failure uses deterministic fallback. Injection forbids
 * drafts; classification may still run.
 */
public final class TriageRole {
    public static final int MAX_OUTPUT_TOKENS = 128;
    public static final float TEMPERATURE = 0.2f;

    private final ModelPort port;
    private final SchemaValidator validator;
    private final PolicyGate gate;
    private final String systemPrompt;

    public TriageRole(ModelPort port, String systemPrompt) {
        this(port, new SchemaValidator(), new PolicyGate(), systemPrompt);
    }

    public TriageRole(
            ModelPort port,
            SchemaValidator validator,
            PolicyGate gate,
            String systemPrompt) {
        this.port = Objects.requireNonNull(port, "port");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
    }

    public TriageRoleResult classify(TriageRoleRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            String prompt = systemPrompt.trim() + "\n\n" + request.getContext().toModelJson().toString();
            ModelResponse response = port.generate(new ModelRequest(prompt, MAX_OUTPUT_TOKENS, TEMPERATURE));
            return acceptOrFallback(request, response);
        } catch (Exception failed) {
            return fallback(request, null, 0, 0, 0L);
        }
    }

    /**
     * Runs a model completion (usually a draft) through PolicyGate. Used by tests and by
     * Stream G if a later role emits a draft on an injection-flagged event.
     */
    public PolicyVerdict reviewDraft(String modelJson, boolean injectionFlagged) {
        return gate.evaluate(PolicyRequest.builder()
                .schema(SchemaName.DRAFTER)
                .modelJson(modelJson)
                .actionType("REPLY")
                .proposalRecipient("Sam")
                .proposalPackageName("com.whatsapp")
                .eventRecipient("Sam")
                .eventPackageName("com.whatsapp")
                .injectionFlagged(injectionFlagged)
                .eventStatus("ACTIVE")
                .tickEventVersion(1)
                .currentEventVersion(1)
                .skepticOk(true)
                .draftText(modelJson)
                .userInstruction("tell him I'm coming")
                .proposedLevel(AttentionLevel.NORMAL)
                .systemConfidence(0.5d)
                .build());
    }

    private TriageRoleResult acceptOrFallback(
            TriageRoleRequest request,
            ModelResponse response) {
        String raw = response == null ? "" : response.getText();
        String json = ModelJson.extractObject(raw);
        int inTok = response == null ? 0 : response.getInputTokens();
        int outTok = response == null ? 0 : response.getOutputTokens();
        long wall = response == null ? 0L : response.getWallMs();
        try {
            JSONObject valid = validator.validate(SchemaName.TRIAGE, json);
            AttentionLevel proposed = AttentionLevel.valueOf(valid.getString("urgency"));
            double system = request.getSystemConfidence() != null
                    ? request.getSystemConfidence()
                    : request.getDeterministic().getAssessment().getScore();
            PolicyVerdict verdict = gate.evaluate(PolicyRequest.builder()
                    .schema(SchemaName.TRIAGE)
                    .modelJson(json)
                    .injectionFlagged(request.injectionFlagged())
                    .eventStatus(request.getEventStatus())
                    .tickEventVersion(request.getTickEventVersion())
                    .currentEventVersion(request.getCurrentEventVersion())
                    .proposedLevel(proposed)
                    .systemConfidence(system)
                    .build());
            if (!verdict.accepted()) {
                return fallback(request, verdict, inTok, outTok, wall);
            }
            AttentionLevel urgency = verdict.effectiveLevel() != null
                    ? verdict.effectiveLevel()
                    : proposed;
            IntelligenceIntent intent = IntelligenceIntent.valueOf(valid.getString("intent"));
            boolean requires = valid.getBoolean("requiresResponse");
            String reason = valid.getString("reason").trim();
            double confidence = valid.getDouble("modelConfidence");
            PriorityAssessment merged = merge(request.getDeterministic(), urgency, reason);
            return new TriageRoleResult(
                    intent,
                    requires,
                    urgency,
                    reason,
                    confidence,
                    AssessmentSource.ON_DEVICE_MODEL,
                    verdict,
                    merged,
                    false,
                    inTok,
                    outTok,
                    wall);
        } catch (Exception failed) {
            return fallback(request, null, inTok, outTok, wall);
        }
    }

    private TriageRoleResult fallback(
            TriageRoleRequest request,
            PolicyVerdict verdict,
            int inputTokens,
            int outputTokens,
            long wallMs) {
        PriorityResult deterministic = request.getDeterministic();
        IntelligenceIntent intent = DeterministicTriageFallback.intent(deterministic, request.getContext());
        boolean requires = DeterministicTriageFallback.requiresResponse(deterministic, request.getContext());
        AttentionLevel urgency = DeterministicTriageFallback.urgency(deterministic);
        String reason = DeterministicTriageFallback.reason(deterministic);
        return new TriageRoleResult(
                intent,
                requires,
                urgency,
                reason,
                null,
                AssessmentSource.DETERMINISTIC_FALLBACK,
                verdict,
                deterministic.getAssessment(),
                true,
                inputTokens,
                outputTokens,
                wallMs);
    }

    private static PriorityAssessment merge(
            PriorityResult deterministic,
            AttentionLevel urgency,
            String reason) {
        double hint = scoreFor(urgency);
        return DeterministicTriage.mergeModelPriority(
                deterministic.getAssessment(),
                new ModelPriorityHint(hint, reason));
    }

    private static double scoreFor(AttentionLevel level) {
        switch (level) {
            case URGENT:
                return 0.85d;
            case IMPORTANT:
                return 0.65d;
            case NORMAL:
                return 0.45d;
            case LOW:
            default:
                return 0.20d;
        }
    }
}
