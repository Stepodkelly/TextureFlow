package com.textureflow.intelligence.roles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public final class TriageRoleTest {
    @Test
    public void acceptsValidTriageJson() {
        ScriptedPort port = new ScriptedPort(
                "{\"intent\":\"REQUEST\",\"requiresResponse\":true,\"urgency\":\"IMPORTANT\","
                        + "\"reason\":\"A recent request.\",\"modelConfidence\":0.61}");
        TriageRole role = new TriageRole(port, loadPrompt());
        TriageRoleResult result = role.classify(request("I'm downstairs, can you come down?", false));
        assertFalse(result.usedFallback());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
        assertEquals(IntelligenceIntent.REQUEST, result.getIntent());
        assertEquals(AttentionLevel.IMPORTANT, result.getUrgency());
        assertTrue(result.isRequiresResponse());
        assertFalse(result.hasDraft());
    }

    @Test
    public void extractsJsonFromMarkdownFence() {
        ScriptedPort port = new ScriptedPort("```json\n"
                + "{\"intent\":\"QUESTION\",\"requiresResponse\":true,\"urgency\":\"NORMAL\","
                + "\"reason\":\"A recent question.\",\"modelConfidence\":0.55}\n```");
        TriageRole role = new TriageRole(port, loadPrompt());
        TriageRoleResult result = role.classify(request("Are you free later?", false));
        assertEquals(IntelligenceIntent.QUESTION, result.getIntent());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, result.getSource());
    }

    @Test
    public void fallsBackWhenSchemaFailsOrPortMissing() {
        TriageRole bad = new TriageRole(new ScriptedPort("not-json"), loadPrompt());
        TriageRoleResult fromBad = bad.classify(request("I'm downstairs, can you come down?", false));
        assertTrue(fromBad.usedFallback());
        assertEquals(AssessmentSource.DETERMINISTIC_FALLBACK, fromBad.getSource());
        assertEquals(IntelligenceIntent.REQUEST_FOR_IMMEDIATE_ACTION, fromBad.getIntent());

        TriageRole none = new TriageRole(new NoModelPort(), loadPrompt());
        TriageRoleResult fromNone = none.classify(request("Sale 50% off today only", false));
        assertTrue(fromNone.usedFallback());
        assertEquals(IntelligenceIntent.PROMOTION, fromNone.getIntent());
        assertFalse(fromNone.hasDraft());
    }

    static TriageRoleRequest request(String body, boolean injection) {
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
        return new TriageRoleRequest(
                context,
                deterministic,
                "ACTIVE",
                1,
                1,
                deterministic.getAssessment().getScore());
    }

    static String loadPrompt() {
        Path[] candidates = {
                Paths.get("app/src/main/assets/intelligence/prompts/triage.v1.txt"),
                Paths.get("src/main/assets/intelligence/prompts/triage.v1.txt"),
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                try {
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("missing triage.v1.txt");
    }

    static final class ScriptedPort implements ModelPort {
        private final String text;

        ScriptedPort(String text) {
            this.text = text;
        }

        @Override
        public void load() {}

        @Override
        public ModelResponse generate(ModelRequest request) {
            return new ModelResponse(text, 12, 6, 9);
        }

        @Override
        public void unload() {}

        @Override
        public int tokenCount(String text) {
            return new NoModelPort().tokenCount(text);
        }

        @Override
        public String runtimeName() {
            return "scripted";
        }
    }
}
