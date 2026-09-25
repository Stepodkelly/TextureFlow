package com.textureflow.intelligence.policy;

/** §6.4 A0: injection flag set → no REPLY proposal from the model. */
public final class InjectionNoDraftRule implements PolicyRule {
    @Override
    public String name() {
        return PolicyRules.INJECTION_NO_DRAFT;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (!request.injectionFlagged()) {
            return;
        }
        if (request.isReplyDraft()) {
            accumulator.drop(name());
        }
    }
}
