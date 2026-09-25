package com.textureflow.intelligence.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AttentionLevel;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class PolicyGateRulesTest {
    private final PolicyGate gate = new PolicyGate();

    @Test
    public void exposesEveryDocRowAsANamedRule() {
        assertEquals(
                Arrays.asList(
                        PolicyRules.VALID_SCHEMA,
                        PolicyRules.ALLOWED_ACTION_TYPE,
                        PolicyRules.RECIPIENT_BINDING,
                        PolicyRules.EVENT_FRESHNESS,
                        PolicyRules.INJECTION_NO_DRAFT,
                        PolicyRules.SKEPTIC_OK,
                        PolicyRules.NO_INVENTED_SECRETS,
                        PolicyRules.URGENT_CONFIDENCE_CAP),
                gate.ruleNames());
    }

    @Test
    public void validSchemaDropsMalformedModelJson() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft()
                .modelJson("{\"replyText\":\"I'm coming down.\",\"tone\":\"DIRECT\",\"command\":\"SEND_NOW\"}")
                .build());
        assertDropped(verdict, PolicyRules.VALID_SCHEMA);
    }

    @Test
    public void allowedActionTypeDropsUnknownActions() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft().actionType("SEND_NOW").build());
        assertDropped(verdict, PolicyRules.ALLOWED_ACTION_TYPE);
    }

    @Test
    public void recipientBindingDropsRecipientOrPackageMismatch() {
        PolicyVerdict recipient = gate.evaluate(acceptedDraft().proposalRecipient("Maya").build());
        assertDropped(recipient, PolicyRules.RECIPIENT_BINDING);

        PolicyVerdict pack = gate.evaluate(acceptedDraft().proposalPackageName("com.evil.app").build());
        assertDropped(pack, PolicyRules.RECIPIENT_BINDING);
    }

    @Test
    public void eventFreshnessMarksRemovedOrChangedEventsStale() {
        PolicyVerdict removed = gate.evaluate(acceptedDraft().eventStatus("REMOVED").build());
        assertTrue(removed.stale());
        assertFalse(removed.accepted());
        assertTrue(removed.hasReason(PolicyRules.EVENT_FRESHNESS));

        PolicyVerdict changed = gate.evaluate(acceptedDraft().currentEventVersion(2).build());
        assertTrue(changed.stale());
        assertTrue(changed.hasReason(PolicyRules.EVENT_FRESHNESS));
    }

    @Test
    public void injectionNoDraftDropsModelReplyOnFlaggedEvents() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft().injectionFlagged(true).build());
        assertDropped(verdict, PolicyRules.INJECTION_NO_DRAFT);
    }

    @Test
    public void skepticOkDropsFailedSkepticReview() {
        PolicyVerdict fromFlag = gate.evaluate(acceptedDraft().skepticOk(false).build());
        assertDropped(fromFlag, PolicyRules.SKEPTIC_OK);

        PolicyVerdict fromJson = gate.evaluate(PolicyRequest.builder()
                .schema(SchemaName.SKEPTIC)
                .modelJson("{\"ok\":false,\"issues\":[\"follows_injection\"]}")
                .build());
        assertDropped(fromJson, PolicyRules.SKEPTIC_OK);
    }

    @Test
    public void noInventedSecretsDropsUrlsPhonesAndCodesAbsentFromInstruction() {
        PolicyVerdict url = gate.evaluate(acceptedDraft()
                .draftText("See https://evil.example/reset")
                .userInstruction("tell him I'm coming")
                .build());
        assertDropped(url, PolicyRules.NO_INVENTED_SECRETS);

        PolicyVerdict phone = gate.evaluate(acceptedDraft()
                .draftText("Call me at +1 415-555-0100")
                .userInstruction("tell him I'm coming")
                .build());
        assertDropped(phone, PolicyRules.NO_INVENTED_SECRETS);

        PolicyVerdict code = gate.evaluate(acceptedDraft()
                .draftText("The code is 482911")
                .userInstruction("tell him I'm coming")
                .build());
        assertDropped(code, PolicyRules.NO_INVENTED_SECRETS);
    }

    @Test
    public void urgentConfidenceCapReducesUntrustedUrgentLevels() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft()
                .proposedLevel(AttentionLevel.URGENT)
                .systemConfidence(0.4d)
                .build());
        assertTrue(verdict.accepted());
        assertTrue(verdict.capped());
        assertFalse(verdict.dropped());
        assertEquals(AttentionLevel.IMPORTANT, verdict.effectiveLevel());
        assertTrue(verdict.hasReason(PolicyRules.URGENT_CONFIDENCE_CAP));
    }

    @Test
    public void acceptedDraftPassesEveryRule() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft().build());
        assertTrue(verdict.accepted());
        assertFalse(verdict.dropped());
        assertFalse(verdict.stale());
        assertFalse(verdict.capped());
        assertEquals(List.of(), verdict.reasons());
    }

    @Test
    public void noOpIsAnAllowedActionType() {
        PolicyVerdict verdict = gate.evaluate(acceptedDraft().actionType("NO_OP").build());
        assertTrue(verdict.accepted());
    }

    private static PolicyRequest.Builder acceptedDraft() {
        return PolicyRequest.builder()
                .schema(SchemaName.DRAFTER)
                .modelJson("{\"replyText\":\"I'm coming down.\",\"tone\":\"DIRECT\"}")
                .actionType("REPLY")
                .proposalRecipient("Sam")
                .proposalPackageName("com.whatsapp")
                .eventRecipient("Sam")
                .eventPackageName("com.whatsapp")
                .eventStatus("ACTIVE")
                .tickEventVersion(1)
                .currentEventVersion(1)
                .injectionFlagged(false)
                .skepticOk(true)
                .draftText("I'm coming down.")
                .userInstruction("tell him I'm coming")
                .proposedLevel(AttentionLevel.NORMAL)
                .systemConfidence(0.7d);
    }

    private static void assertDropped(PolicyVerdict verdict, String rule) {
        assertTrue(rule + " should drop: " + verdict.reasons(), verdict.dropped());
        assertFalse(verdict.accepted());
        assertTrue(verdict.hasReason(rule));
    }
}
