package com.textureflow.intelligence.policy;

import org.json.JSONObject;

/** §6.4: draft URLs, phone numbers, or codes must already appear in the user's instruction. */
public final class NoInventedSecretsRule implements PolicyRule {
    @Override
    public String name() {
        return PolicyRules.NO_INVENTED_SECRETS;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        String draft = resolveDraftText(request, accumulator);
        if (draft.isEmpty() && !request.isReplyDraft()) {
            return;
        }
        if (draft.isEmpty()) {
            return;
        }
        if (!InventedSecretScanner.secretsNotInInstruction(draft, request.userInstruction()).isEmpty()) {
            accumulator.drop(name());
        }
    }

    private static String resolveDraftText(PolicyRequest request, PolicyAccumulator accumulator) {
        if (!request.draftText().isEmpty()) {
            return request.draftText();
        }
        JSONObject parsed = accumulator.parsedJson();
        if (parsed == null) {
            return "";
        }
        if (parsed.has("replyText")) {
            return parsed.optString("replyText", "");
        }
        if (parsed.has("text")) {
            return parsed.optString("text", "");
        }
        return "";
    }
}
