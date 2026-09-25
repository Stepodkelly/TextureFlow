package com.textureflow.intelligence.policy;

/** §6.4: event still ACTIVE and {@code eventVersion} unchanged since tick start → else STALE. */
public final class EventFreshnessRule implements PolicyRule {
    @Override
    public String name() {
        return PolicyRules.EVENT_FRESHNESS;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        boolean hasStatus = request.eventStatus() != null;
        boolean hasVersions = request.tickEventVersion() != null || request.currentEventVersion() != null;
        if (!hasStatus && !hasVersions) {
            return;
        }
        if (hasStatus && !"ACTIVE".equalsIgnoreCase(request.eventStatus())) {
            accumulator.markStale(name());
            return;
        }
        if (hasVersions) {
            Integer tick = request.tickEventVersion();
            Integer current = request.currentEventVersion();
            if (tick == null || current == null || !tick.equals(current)) {
                accumulator.markStale(name());
            }
        }
    }
}
