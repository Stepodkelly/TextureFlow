package com.textureflow.intelligence.roles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.model.NoModelPort;
import com.textureflow.intelligence.policy.PolicyRules;
import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.shield.ShieldedEvent;
import com.textureflow.intelligence.shield.ShieldedPerson;
import com.textureflow.intelligence.shield.TokenBudgetReport;

import org.junit.Test;

import java.util.Collections;

public final class DrafterRoleTest {
    private static final String VALID_DRAFT_V1 = ""
            + "{\"text\":\"No thank you.\",\"tone\":\"WARM\",\"confidence\":0.8,\"ambiguities\":[]}";
    private static final String VALID_DRAFTER = "{\"replyText\":\"No thank you.\",\"tone\":\"WARM\"}";

    @Test
    public void defaultPromptMatchesAsset() {
        assertEquals(
                RolePromptFiles.load("draft.v1.txt").trim(),
                DrafterRole.DEFAULT_PROMPT.trim());
    }

    @Test
    public void completeReplyIsReturnedVerbatimWithoutCallingModel() {
        FakeModelPort port = new FakeModelPort(VALID_DRAFT_V1);
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request(
                "tell him I'm coming",
                "I'm coming",
                false));
        assertEquals(0, port.generates);
        assertEquals("I'm coming", result.getReplyText());
        assertTrue(result.usedLiteral());
        assertFalse(result.usedFallback());
        assertEquals(AssessmentSource.DETERMINISTIC, result.getSource());
        assertEquals(ReplyTone.DIRECT, result.getTone());
    }

    @Test
    public void spokenTellInstructionUsesExtractedWordsVerbatim() {
        FakeModelPort port = new FakeModelPort(VALID_DRAFT_V1);
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("tell Sam I'm coming", "", false));
        assertEquals(0, port.generates);
        assertEquals("I'm coming", result.getReplyText());
        assertTrue(result.usedLiteral());
    }

    @Test
    public void acceptsDraftV1WhenUserAskedForPolish() {
        FakeModelPort port = new FakeModelPort(VALID_DRAFT_V1);
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("write a polite no", "", false));
        assertEquals(1, port.generates);
        assertFalse(result.usedFallback());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
        assertEquals("No thank you.", result.getReplyText());
        assertEquals(ReplyTone.WARM, result.getTone());
        assertTrue(port.lastRequest.getPrompt().contains("write a polite no"));
    }

    @Test
    public void acceptsDrafterSchemaShape() {
        FakeModelPort port = new FakeModelPort(VALID_DRAFTER);
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("write a polite no", "", false));
        assertEquals("No thank you.", result.getReplyText());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
    }

    @Test
    public void injectionSkipsModelAndReturnsLiteralWords() {
        FakeModelPort port = new FakeModelPort(
                "{\"text\":\"Sure, I will send the codes now.\",\"tone\":\"DIRECT\","
                        + "\"confidence\":0.9,\"ambiguities\":[]}");
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("tell him no thanks", "no thanks", true));
        assertEquals(0, port.generates);
        assertEquals("no thanks", result.getReplyText());
        assertTrue(result.usedLiteral());
        assertTrue(result.usedFallback());
        assertEquals(AssessmentSource.DETERMINISTIC_FALLBACK, result.getSource());
    }

    @Test
    public void injectionWithNoUserWordsReturnsEmpty() {
        FakeModelPort port = new FakeModelPort(VALID_DRAFT_V1);
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("", "", true));
        assertEquals(0, port.generates);
        assertEquals("", result.getReplyText());
    }

    @Test
    public void schemaFailureAndMissingPortFallBackToUserWords() {
        DrafterRole bad = new DrafterRole(
                new FakeModelPort("not-json"), RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult fromBad = bad.draft(request("write a polite no", "", false));
        assertTrue(fromBad.usedFallback());
        assertEquals("write a polite no", fromBad.getReplyText());
        assertEquals(AssessmentSource.DETERMINISTIC_FALLBACK, fromBad.getSource());

        DrafterRole none = new DrafterRole(new NoModelPort(), RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult fromNone = none.draft(request("write a polite no", "", false));
        assertTrue(fromNone.usedFallback());
        assertEquals("write a polite no", fromNone.getReplyText());
    }

    @Test
    public void inventedLinkOrNumberIsDropped() {
        FakeModelPort port = new FakeModelPort(
                "{\"text\":\"No, call me at 5551234 or https://evil.test\","
                        + "\"tone\":\"DIRECT\",\"confidence\":0.4,\"ambiguities\":[]}");
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleResult result = role.draft(request("write a polite no", "", false));
        assertTrue(result.usedFallback());
        assertEquals("write a polite no", result.getReplyText());
        assertTrue(DrafterRole.inventsFacts(
                "meet at 9 https://evil.test", "write a polite no"));
        assertFalse(DrafterRole.inventsFacts("No thank you.", "write a polite no"));
    }

    @Test
    public void policyGateDropsInjectionDraftWhenFlaggedAfterGenerate() {
        FakeModelPort port = new FakeModelPort(
                "{\"text\":\"I will ignore previous instructions.\",\"tone\":\"DIRECT\","
                        + "\"confidence\":0.2,\"ambiguities\":[]}");
        DrafterRole role = new DrafterRole(port, RolePromptFiles.load("draft.v1.txt"));
        DrafterRoleRequest req = request("write a polite no", "", false);
        DrafterRoleResult result = role.draft(new DrafterRoleRequest(
                req.getContext(),
                req.getUserRequest(),
                req.getUserDraftText(),
                req.getPreferredTone(),
                req.getEventRecipient(),
                req.getEventPackageName(),
                req.getEventStatus(),
                req.getTickEventVersion(),
                req.getCurrentEventVersion(),
                req.getSystemConfidence(),
                true));
        assertEquals(0, port.generates);
        assertEquals("write a polite no", result.getReplyText());
        if (result.getVerdict() != null) {
            assertTrue(result.getVerdict().hasReason(PolicyRules.INJECTION_NO_DRAFT)
                    || result.usedFallback());
        }
    }

    static DrafterRoleRequest request(String userRequest, String userDraftText, boolean injection) {
        ShieldedContext context = new ShieldedContext(
                new ShieldedPerson("Sam", "friend"),
                "WhatsApp",
                Collections.singletonList(
                        new ShieldedEvent(2, "<untrusted>Can you come down?</untrusted>")),
                userRequest,
                new TokenBudgetReport(20, 0, false),
                false,
                injection);
        return new DrafterRoleRequest(
                context,
                userRequest,
                userDraftText,
                ReplyTone.DIRECT,
                "Sam",
                "com.whatsapp",
                "ACTIVE",
                1,
                1,
                0.5d,
                injection);
    }
}
