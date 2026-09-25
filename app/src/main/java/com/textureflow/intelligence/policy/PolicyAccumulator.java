package com.textureflow.intelligence.policy;

import com.textureflow.intelligence.api.AttentionLevel;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class PolicyAccumulator {
    private boolean dropped;
    private boolean stale;
    private boolean capped;
    private final List<String> reasons = new ArrayList<>();
    private AttentionLevel effectiveLevel;
    private JSONObject parsedJson;

    void drop(String ruleName) {
        dropped = true;
        addReason(ruleName);
    }

    void markStale(String ruleName) {
        stale = true;
        addReason(ruleName);
    }

    void capLevel(String ruleName, AttentionLevel level) {
        capped = true;
        effectiveLevel = level;
        addReason(ruleName);
    }

    void parsedJson(JSONObject parsedJson) {
        this.parsedJson = parsedJson;
    }

    JSONObject parsedJson() {
        return parsedJson;
    }

    PolicyVerdict toVerdict() {
        boolean accepted = !dropped && !stale;
        return new PolicyVerdict(accepted, capped, stale, dropped, reasons, effectiveLevel);
    }

    private void addReason(String ruleName) {
        if (!reasons.contains(ruleName)) {
            reasons.add(ruleName);
        }
    }
}
