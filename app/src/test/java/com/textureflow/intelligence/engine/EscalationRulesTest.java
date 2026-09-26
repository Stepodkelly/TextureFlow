package com.textureflow.intelligence.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.ledger.TickTrigger;
import com.textureflow.intelligence.triage.PriorityFeatures;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class EscalationRulesTest {
    private final String name;
    private final PriorityFeatures features;
    private final TickTrigger trigger;
    private final com.textureflow.intelligence.api.CapabilityProfile capability;
    private final EscalationDecision.Rule expected;

    public EscalationRulesTest(
            String name,
            PriorityFeatures features,
            TickTrigger trigger,
            com.textureflow.intelligence.api.CapabilityProfile capability,
            EscalationDecision.Rule expected) {
        this.name = name;
        this.features = features;
        this.trigger = trigger;
        this.capability = capability;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> rows() {
        return Arrays.asList(new Object[][] {
                {
                        "A0 injection on posted",
                        features(1.0, 0, 0, false, true),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A0
                },
                {
                        "A0 beats user query",
                        features(1.0, 1.0, 1.0, false, true),
                        TickTrigger.USER_QUERY,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A0
                },
                {
                        "A1 promotion from unknown person",
                        features(0.5, 0, 0, true, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A1
                },
                {
                        "A1 promotion beats user query",
                        features(0.5, 0, 0, true, false),
                        TickTrigger.USER_QUERY,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A1
                },
                {
                        "A2 known person strong urgency",
                        features(1.0, 1.0, 0, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A2
                },
                {
                        "A2 known person strong request",
                        features(0.85, 0, 1.0, false, false),
                        TickTrigger.EVENT_UPDATED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A2
                },
                {
                        "A2 still publishes without a model",
                        features(1.0, 0.8, 0.75, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.deterministicOnly(),
                        EscalationDecision.Rule.A2
                },
                {
                        "A3 unknown conversational on T2",
                        features(0.5, 0, 0.75, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A3
                },
                {
                        "A3 ambiguous with no strong signals",
                        features(0.5, 0, 0, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T1),
                        EscalationDecision.Rule.A3
                },
                {
                        "A3 known person without strong pattern",
                        features(1.0, 0, 0, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A3
                },
                {
                        "A3 promo from known person is not A1",
                        features(1.0, 0, 0, true, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A3
                },
                {
                        "A4 user query on T2",
                        features(0.5, 0, 0, false, false),
                        TickTrigger.USER_QUERY,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A4
                },
                {
                        "A4 draft request on T2",
                        features(1.0, 1.0, 1.0, false, false),
                        TickTrigger.DRAFT_REQUEST,
                        CapabilityProfiles.onDevice(CapabilityTier.T2),
                        EscalationDecision.Rule.A4
                },
                {
                        "A5 unknown conversational on T0",
                        features(0.5, 0, 0, false, false),
                        TickTrigger.EVENT_POSTED,
                        CapabilityProfiles.deterministicOnly(),
                        EscalationDecision.Rule.A5
                },
                {
                        "A5 user query on T0",
                        features(0.5, 0, 0, false, false),
                        TickTrigger.USER_QUERY,
                        CapabilityProfiles.deterministicOnly(),
                        EscalationDecision.Rule.A5
                },
                {
                        "A5 draft request when thermal severe",
                        features(0.5, 0, 0, false, false),
                        TickTrigger.DRAFT_REQUEST,
                        CapabilityProfiles.thermalSevere(CapabilityTier.T2),
                        EscalationDecision.Rule.A5
                },
                {
                        "A5 ambiguous when thermal severe",
                        features(0.5, 0.2, 0, false, false),
                        TickTrigger.PERIODIC_REFRESH,
                        CapabilityProfiles.thermalSevere(CapabilityTier.T3),
                        EscalationDecision.Rule.A5
                },
        });
    }

    @Test
    public void rowMatchesDoc() {
        EscalationDecision decision = EscalationRules.decide(features, trigger, capability);
        assertEquals(name, expected, decision.getRule());
        if (expected == EscalationDecision.Rule.A0) {
            assertTrue(decision.forbidDrafts());
            assertFalse(decision.runsModel());
        }
        if (expected == EscalationDecision.Rule.A1) {
            assertEquals(com.textureflow.intelligence.api.AttentionLevel.LOW, decision.capLevel());
            assertFalse(decision.runsModel());
        }
        if (expected == EscalationDecision.Rule.A3 || expected == EscalationDecision.Rule.A4) {
            assertTrue(decision.runsModel());
        }
        if (expected == EscalationDecision.Rule.A5) {
            assertEquals(
                    com.textureflow.intelligence.api.AssessmentSource.DETERMINISTIC_FALLBACK,
                    decision.source());
            assertFalse(decision.runsModel());
        }
    }

    private static PriorityFeatures features(
            double importance,
            double urgency,
            double request,
            boolean promotional,
            boolean malicious) {
        return new PriorityFeatures(importance, urgency, request, 1.0, 0.9, promotional, malicious);
    }
}
