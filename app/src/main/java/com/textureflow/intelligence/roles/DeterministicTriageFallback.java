package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.shield.ShieldedEvent;
import com.textureflow.intelligence.triage.PriorityFeatures;
import com.textureflow.intelligence.triage.PriorityResult;

/** Maps deterministic features to the §6.1 triage shape when the model cannot be used. */
final class DeterministicTriageFallback {
    private DeterministicTriageFallback() {}

    static IntelligenceIntent intent(PriorityResult deterministic, ShieldedContext context) {
        PriorityFeatures features = deterministic.getFeatures();
        if (features.isMaliciousInstruction()) {
            return IntelligenceIntent.UNKNOWN;
        }
        if (features.isPromotional()) {
            return IntelligenceIntent.PROMOTION;
        }
        if (features.getUrgencySignals() >= 0.8 && features.getDirectRequest() >= 0.75) {
            return IntelligenceIntent.REQUEST_FOR_IMMEDIATE_ACTION;
        }
        if (features.getDirectRequest() >= 0.75) {
            return IntelligenceIntent.REQUEST;
        }
        String body = firstBody(context);
        if (body.contains("?")) {
            return IntelligenceIntent.QUESTION;
        }
        return body.isEmpty() ? IntelligenceIntent.UNKNOWN : IntelligenceIntent.INFORMATION;
    }

    static boolean requiresResponse(PriorityResult deterministic, ShieldedContext context) {
        PriorityFeatures features = deterministic.getFeatures();
        if (features.isMaliciousInstruction() || features.isPromotional()) {
            return false;
        }
        return features.getDirectRequest() > 0 || firstBody(context).contains("?");
    }

    static AttentionLevel urgency(PriorityResult deterministic) {
        return deterministic.getAssessment().getLevel();
    }

    static String reason(PriorityResult deterministic) {
        return deterministic.getAssessment().getReason();
    }

    private static String firstBody(ShieldedContext context) {
        if (context == null || context.getEvents().isEmpty()) {
            return "";
        }
        ShieldedEvent first = context.getEvents().get(0);
        return first.getText() == null ? "" : first.getText();
    }
}
