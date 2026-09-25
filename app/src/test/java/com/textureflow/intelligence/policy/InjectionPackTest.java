package com.textureflow.intelligence.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.shield.InjectionDetector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class InjectionPackTest {
    private final PolicyGate gate = new PolicyGate();

    @Test
    public void packFlagsTsAndThreatModelStringsAndDropsModelDrafts() throws Exception {
        JSONArray cases = loadCases();
        assertTrue("injection pack must not be empty", cases.length() >= 16);
        int flagged = 0;
        int bindingDrops = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject row = cases.getJSONObject(i);
            String id = row.getString("id");
            String body = row.getString("body");
            boolean expectFlagged = row.getBoolean("expectFlagged");
            assertEquals(id, expectFlagged, InjectionDetector.containsUntrustedInstruction(body));

            if (expectFlagged) {
                flagged += 1;
                PolicyVerdict verdict = gate.evaluate(draftRequest(row)
                        .injectionFlagged(true)
                        .build());
                assertTrue(id + " must drop a model draft: " + verdict.reasons(), verdict.dropped());
                assertTrue(id, verdict.hasReason(PolicyRules.INJECTION_NO_DRAFT));
                assertFalse(id, verdict.accepted());
            }
            if (row.has("expectDropReasons")) {
                bindingDrops += 1;
                PolicyVerdict verdict = gate.evaluate(draftRequest(row).build());
                JSONArray reasons = row.getJSONArray("expectDropReasons");
                for (int r = 0; r < reasons.length(); r++) {
                    assertTrue(id + " missing " + reasons.getString(r), verdict.hasReason(reasons.getString(r)));
                }
                assertTrue(id, verdict.dropped());
            }
        }
        assertTrue("expected flagged injection strings", flagged >= 14);
        assertEquals(2, bindingDrops);
    }

    private static PolicyRequest.Builder draftRequest(JSONObject row) {
        String recipient = row.optString("eventRecipient", "Sam");
        String proposalRecipient = row.optString("proposalRecipient", recipient);
        String pack = row.optString("eventPackageName", "com.whatsapp");
        String proposalPack = row.optString("proposalPackageName", pack);
        return PolicyRequest.builder()
                .schema(SchemaName.DRAFTER)
                .modelJson("{\"replyText\":\"Sure, I will do that.\",\"tone\":\"DIRECT\"}")
                .actionType("REPLY")
                .proposalRecipient(proposalRecipient)
                .proposalPackageName(proposalPack)
                .eventRecipient(recipient)
                .eventPackageName(pack)
                .eventStatus("ACTIVE")
                .tickEventVersion(1)
                .currentEventVersion(1)
                .injectionFlagged(false)
                .skepticOk(true)
                .draftText("Sure, I will do that.")
                .userInstruction("tell him I'm coming")
                .proposedLevel(AttentionLevel.NORMAL)
                .systemConfidence(0.7d);
    }

    private static JSONArray loadCases() throws Exception {
        try (InputStream in = InjectionPackTest.class.getResourceAsStream("/intelligence/injection-cases.json")) {
            if (in == null) {
                fail("missing test resource /intelligence/injection-cases.json");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONArray(json);
        }
    }
}
