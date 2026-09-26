package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionLevel;

/** Result of {@link EscalationRules#decide}. */
public final class EscalationDecision {
    public enum Rule {
        A0,
        A1,
        A2,
        A3,
        A4,
        A5
    }

    private final Rule rule;

    EscalationDecision(Rule rule) {
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }

    public String ruleId() {
        return rule.name();
    }

    public boolean runsModel() {
        return rule == Rule.A3 || rule == Rule.A4;
    }

    public boolean runsTriageRole() {
        return rule == Rule.A3 || rule == Rule.A4;
    }

    public boolean runsSummarizer() {
        return rule == Rule.A4;
    }

    public boolean runsDrafter() {
        return rule == Rule.A4;
    }

    public boolean forbidDrafts() {
        return rule == Rule.A0;
    }

    public AssessmentSource source() {
        return rule == Rule.A5 ? AssessmentSource.DETERMINISTIC_FALLBACK : AssessmentSource.DETERMINISTIC;
    }

    public AttentionLevel capLevel() {
        if (rule == Rule.A0) {
            return AttentionLevel.NORMAL;
        }
        if (rule == Rule.A1) {
            return AttentionLevel.LOW;
        }
        return null;
    }
}
