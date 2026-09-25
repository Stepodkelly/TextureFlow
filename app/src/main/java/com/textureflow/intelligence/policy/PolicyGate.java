package com.textureflow.intelligence.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Deterministic NormLayer: every §6.4 check is a named rule. */
public final class PolicyGate {
    public static final double DEFAULT_URGENT_SYSTEM_CONFIDENCE =
            UrgentConfidenceCapRule.DEFAULT_THRESHOLD;

    private final List<PolicyRule> rules;

    public PolicyGate() {
        this(new SchemaValidator(), DEFAULT_URGENT_SYSTEM_CONFIDENCE);
    }

    public PolicyGate(SchemaValidator validator) {
        this(validator, DEFAULT_URGENT_SYSTEM_CONFIDENCE);
    }

    public PolicyGate(SchemaValidator validator, double urgentSystemConfidence) {
        Objects.requireNonNull(validator, "validator");
        this.rules = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(
                new ValidSchemaRule(validator),
                new AllowedActionTypeRule(),
                new RecipientBindingRule(),
                new EventFreshnessRule(),
                new InjectionNoDraftRule(),
                new SkepticOkRule(),
                new NoInventedSecretsRule(),
                new UrgentConfidenceCapRule(urgentSystemConfidence))));
    }

    public List<String> ruleNames() {
        List<String> names = new ArrayList<>();
        for (PolicyRule rule : rules) {
            names.add(rule.name());
        }
        return Collections.unmodifiableList(names);
    }

    public PolicyVerdict evaluate(PolicyRequest request) {
        Objects.requireNonNull(request, "request");
        PolicyAccumulator accumulator = new PolicyAccumulator();
        for (PolicyRule rule : rules) {
            rule.evaluate(request, accumulator);
        }
        return accumulator.toVerdict();
    }
}
