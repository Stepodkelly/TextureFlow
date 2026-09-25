package com.textureflow.intelligence.policy;

/** §6.4 / T03 / T13: proposal recipient and package must equal the event's resolved identity. */
public final class RecipientBindingRule implements PolicyRule {
    @Override
    public String name() {
        return PolicyRules.RECIPIENT_BINDING;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (!request.isActionProposal() || "NO_OP".equals(request.actionType())) {
            return;
        }
        if (request.eventRecipient().isEmpty()
                || request.eventPackageName().isEmpty()
                || !request.eventRecipient().equals(request.proposalRecipient())
                || !request.eventPackageName().equals(request.proposalPackageName())) {
            accumulator.drop(name());
        }
    }
}
