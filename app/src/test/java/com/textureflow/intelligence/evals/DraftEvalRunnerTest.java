package com.textureflow.intelligence.evals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.roles.DrafterRole;
import com.textureflow.intelligence.roles.DrafterRoleRequest;
import com.textureflow.intelligence.roles.DrafterRoleResult;
import com.textureflow.intelligence.roles.FakeModelPort;
import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.shield.ShieldedEvent;
import com.textureflow.intelligence.shield.ShieldedPerson;
import com.textureflow.intelligence.shield.TokenBudgetReport;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

public final class DraftEvalRunnerTest {
    @Test
    public void literalWordsFirstAndInjectionNeverFollowModel() throws Exception {
        JSONArray cases = new JSONArray(new String(
                Files.readAllBytes(EvalPaths.draftCases()), StandardCharsets.UTF_8));
        assertTrue("expected Wave 3 draft cases", cases.length() >= 5);
        int injectionPass = 0;
        int injectionTotal = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject row = cases.getJSONObject(i);
            boolean injection = row.optBoolean("injection", false);
            FakeModelPort port = new FakeModelPort(row.getString("modelJson"));
            DrafterRole role = new DrafterRole(port, DrafterRole.DEFAULT_PROMPT);
            DrafterRoleResult result = role.draft(request(
                    row.getString("userRequest"),
                    row.getString("userDraftText"),
                    injection));
            assertEquals(row.getString("id"), row.getString("expectedReply"), result.getReplyText());
            assertEquals(row.getString("id"), row.getBoolean("expectModelCall"), port.generates > 0);
            if (injection) {
                injectionTotal++;
                if (port.generates == 0
                        && result.getReplyText().equals(row.getString("expectedReply"))) {
                    injectionPass++;
                }
            }
        }
        assertEquals("injection drafts must stay on the user's words", injectionTotal, injectionPass);
    }

    private static DrafterRoleRequest request(
            String userRequest, String userDraftText, boolean injection) {
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
