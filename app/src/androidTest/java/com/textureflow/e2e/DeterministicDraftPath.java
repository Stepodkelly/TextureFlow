package com.textureflow.e2e;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.policy.PolicyGate;
import com.textureflow.intelligence.policy.PolicyRequest;
import com.textureflow.intelligence.policy.PolicyVerdict;
import com.textureflow.intelligence.policy.SchemaName;
import com.textureflow.intelligence.shield.InjectionDetector;
import com.textureflow.intelligence.triage.PriorityPatterns;

/**
 * Deterministic-only draft path used by e2e: injection never yields a {@link ProposalDraft}.
 * No on-device model is invoked.
 */
final class DeterministicDraftPath {
    private static final PolicyGate GATE = new PolicyGate();

    private DeterministicDraftPath() {}

    static boolean isInjection(String body) {
        return InjectionDetector.containsUntrustedInstruction(body)
                || PriorityPatterns.containsUntrustedInstruction(body);
    }

    static ProposalDraft maybeModelDraft(String body) {
        if (isInjection(body)) {
            return null;
        }
        return null;
    }

    static PolicyVerdict evaluateModelReplyDraft(String body, boolean injectionFlagged) {
        return GATE.evaluate(PolicyRequest.builder()
                .schema(SchemaName.DRAFTER)
                .modelJson("{\"replyText\":\"Sure, I will do that.\",\"tone\":\"DIRECT\"}")
                .actionType("REPLY")
                .proposalRecipient("Sam")
                .proposalPackageName("com.whatsapp")
                .eventRecipient("Sam")
                .eventPackageName("com.whatsapp")
                .eventStatus("ACTIVE")
                .tickEventVersion(1)
                .currentEventVersion(1)
                .injectionFlagged(injectionFlagged)
                .skepticOk(true)
                .draftText("Sure, I will do that.")
                .userInstruction("reply")
                .proposedLevel(AttentionLevel.NORMAL)
                .systemConfidence(0.7d)
                .build());
    }
}
