package com.textureflow.intelligence.policy;

import org.json.JSONObject;

/** §6.4: Skeptic {@code ok=false} → use the user's literal words or no draft. */
public final class SkepticOkRule implements PolicyRule {
    @Override
    public String name() {
        return PolicyRules.SKEPTIC_OK;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (request.skepticOk() != null) {
            if (!request.skepticOk()) {
                accumulator.drop(name());
            }
            return;
        }
        JSONObject parsed = accumulator.parsedJson();
        if (request.schema() == SchemaName.SKEPTIC && parsed != null && !parsed.optBoolean("ok", false)) {
            accumulator.drop(name());
        }
    }
}
