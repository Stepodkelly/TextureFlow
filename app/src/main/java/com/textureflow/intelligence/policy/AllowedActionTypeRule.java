package com.textureflow.intelligence.policy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * §6.4: {@code actionType} ∈ {REPLY, SNOOZE, DISMISS, NO_OP}.
 * {@code NO_OP} is allowed here even though {@code ActionType} does not declare it yet.
 */
public final class AllowedActionTypeRule implements PolicyRule {
    public static final Set<String> ALLOWED = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("REPLY", "SNOOZE", "DISMISS", "NO_OP")));

    @Override
    public String name() {
        return PolicyRules.ALLOWED_ACTION_TYPE;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (!request.isActionProposal()) {
            return;
        }
        if (!ALLOWED.contains(request.actionType())) {
            accumulator.drop(name());
        }
    }
}
