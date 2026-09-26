package com.textureflow.intelligence.roles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.model.NoModelPort;
import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.shield.ShieldedEvent;
import com.textureflow.intelligence.shield.ShieldedPerson;
import com.textureflow.intelligence.shield.TokenBudgetReport;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.PriorityResult;
import com.textureflow.intelligence.triage.ResolvedIdentity;
import com.textureflow.intelligence.triage.TriageEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class SummarizerRoleTest {
    private static final String VALID_SUMMARY = ""
            + "{\"summary\":\"Sam is downstairs and cannot enter.\","
            + "\"priorityScore\":0.94,\"priorityLevel\":\"URGENT\","
            + "\"priorityReason\":\"A close contact is waiting outside.\","
            + "\"intent\":\"REQUEST_FOR_IMMEDIATE_ACTION\",\"requiresResponse\":true,"
            + "\"ambiguities\":[]}";

    @Test
    public void defaultPromptMatchesAsset() {
        assertEquals(
                RolePromptFiles.load("summary.v1.txt").trim(),
                SummarizerRole.DEFAULT_PROMPT.trim());
    }

    @Test
    public void acceptsValidSummaryV1Json() {
        FakeModelPort port = new FakeModelPort(VALID_SUMMARY);
        SummarizerRole role = new SummarizerRole(port, RolePromptFiles.load("summary.v1.txt"));
        SummarizerRoleResult result = role.summarize(request("I'm downstairs, can you come down?", false));
        assertEquals(1, port.generates);
        assertFalse(result.usedFallback());
        assertTrue(result.isSchemaValid());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
        assertEquals("Sam is downstairs and cannot enter.", result.getSummary());
        assertEquals(AttentionLevel.URGENT, result.getLevel());
        assertEquals(IntelligenceIntent.REQUEST_FOR_IMMEDIATE_ACTION, result.getIntent());
        assertTrue(result.isRequiresResponse());
        assertTrue(result.getAmbiguities().isEmpty());
    }

    @Test
    public void extractsJsonFromMarkdownFence() {
        FakeModelPort port = new FakeModelPort("```json\n" + VALID_SUMMARY + "\n```");
        SummarizerRole role = new SummarizerRole(port, RolePromptFiles.load("summary.v1.txt"));
        SummarizerRoleResult result = role.summarize(request("Dinner at nine?", false));
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
        assertEquals("Sam is downstairs and cannot enter.", result.getSummary());
    }

    @Test
    public void fallsBackWhenSchemaFailsOrPortMissing() {
        SummarizerRole bad = new SummarizerRole(
                new FakeModelPort("not-json"), RolePromptFiles.load("summary.v1.txt"));
        SummarizerRoleResult fromBad = bad.summarize(request("I'm downstairs, can you come down?", false));
        assertTrue(fromBad.usedFallback());
        assertFalse(fromBad.isSchemaValid());
        assertEquals(AssessmentSource.DETERMINISTIC_FALLBACK, fromBad.getSource());
        assertTrue(fromBad.getSummary().contains("I'm downstairs"));

        SummarizerRole none = new SummarizerRole(new NoModelPort(), RolePromptFiles.load("summary.v1.txt"));
        SummarizerRoleResult fromNone = none.summarize(request("Sale 50% off today only", false));
        assertTrue(fromNone.usedFallback());
        assertEquals(IntelligenceIntent.PROMOTION, fromNone.getIntent());
        assertTrue(fromNone.getSummary().contains("Sale 50%"));
    }

    @Test
    public void fallbackConcatenatesNewestShieldedBodiesAndCapsAt240() {
        String newest = "newest body that should come first";
        String older = "older body";
        String tooLong = "x".repeat(300);
        ShieldedContext context = new ShieldedContext(
                new ShieldedPerson("Sam", "friend"),
                "WhatsApp",
                Arrays.asList(
                        new ShieldedEvent(1, "<untrusted>" + newest + "</untrusted>"),
                        new ShieldedEvent(8, "<untrusted>" + older + "</untrusted>"),
                        new ShieldedEvent(20, "<untrusted>" + tooLong + "</untrusted>")),
                "",
                new TokenBudgetReport(20, 0, false),
                false,
                false);
        String summary = SummarizerRole.fallbackSummary(context);
        assertTrue(summary.startsWith(newest));
        assertTrue(summary.contains(older));
        assertEquals(240, summary.length());
        assertFalse(summary.contains("<untrusted>"));
    }

    @Test
    public void fallbackUsesEmptyCopyWhenNoEvents() {
        assertEquals("No recent messages.", SummarizerRole.fallbackSummary(new ShieldedContext(
                new ShieldedPerson("Sam", "friend"),
                "WhatsApp",
                Collections.emptyList(),
                "",
                new TokenBudgetReport(0, 0, false),
                false,
                false)));
    }

    static SummarizerRoleRequest request(String body, boolean injection) {
        long now = 1_700_000_000_000L;
        TriageEvent event = TriageEvent.atMillis(
                "e1", "com.whatsapp", body, now - 120_000L, "Sam", "p1");
        ResolvedIdentity identity = new ResolvedIdentity("p1", "Sam", 0.7d, "friend", "Sam");
        PriorityResult deterministic = DeterministicTriage.assess(event, identity, now);
        ShieldedContext context = new ShieldedContext(
                new ShieldedPerson("Sam", "friend"),
                "WhatsApp",
                Collections.singletonList(new ShieldedEvent(2, "<untrusted>" + body + "</untrusted>")),
                "",
                new TokenBudgetReport(20, 0, false),
                false,
                injection || deterministic.getFeatures().isMaliciousInstruction());
        return new SummarizerRoleRequest(
                context,
                deterministic,
                "ACTIVE",
                1,
                1,
                deterministic.getAssessment().getScore());
    }
}
