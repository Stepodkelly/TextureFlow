package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.ledger.TickTrigger;
import com.textureflow.intelligence.triage.PriorityFeatures;

import java.util.Objects;

/**
 * Doc §5.3 A0–A5. Pure function of features, trigger, and capability.
 * Known person is {@code personImportance >= 0.8} (resolved important contacts).
 */
public final class EscalationRules {
    public static final double KNOWN_PERSON_IMPORTANCE = 0.8d;
    public static final double STRONG_URGENCY = 0.8d;
    public static final double STRONG_REQUEST = 0.75d;

    private EscalationRules() {}

    /**
     * {@code (features, trigger, capability) → Rule}. A3/A4 become A5 when the
     * profile cannot run a model (T0, thermal SEVERE, or runtime port {@code none}).
     */
    public static EscalationDecision decide(
            PriorityFeatures features,
            TickTrigger trigger,
            CapabilityProfile capability) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(capability, "capability");

        EscalationDecision.Rule rule;
        if (features.isMaliciousInstruction()) {
            rule = EscalationDecision.Rule.A0;
        } else if (features.isPromotional() && !knownPerson(features)) {
            rule = EscalationDecision.Rule.A1;
        } else if (trigger == TickTrigger.USER_QUERY || trigger == TickTrigger.DRAFT_REQUEST) {
            rule = EscalationDecision.Rule.A4;
        } else if (knownPerson(features) && strongDeterministic(features)) {
            rule = EscalationDecision.Rule.A2;
        } else {
            rule = EscalationDecision.Rule.A3;
        }

        if ((rule == EscalationDecision.Rule.A3 || rule == EscalationDecision.Rule.A4)
                && !modelUsable(capability)) {
            return new EscalationDecision(EscalationDecision.Rule.A5);
        }
        return new EscalationDecision(rule);
    }

    static boolean knownPerson(PriorityFeatures features) {
        return features.getPersonImportance() >= KNOWN_PERSON_IMPORTANCE;
    }

    static boolean strongDeterministic(PriorityFeatures features) {
        return features.getUrgencySignals() >= STRONG_URGENCY
                || features.getDirectRequest() >= STRONG_REQUEST;
    }

    static boolean modelUsable(CapabilityProfile capability) {
        if (capability.getTier() == CapabilityTier.T0) {
            return false;
        }
        String thermal = capability.getBudget().getThermal();
        if (thermal != null && "severe".equalsIgnoreCase(thermal.trim())) {
            return false;
        }
        String port = capability.getRuntime().getPort();
        return port != null && !port.isEmpty() && !"none".equalsIgnoreCase(port);
    }
}
