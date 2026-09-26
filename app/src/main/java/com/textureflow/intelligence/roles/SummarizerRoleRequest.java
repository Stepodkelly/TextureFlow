package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.triage.PriorityResult;

import java.util.Objects;

/** One A4 summary call. Prompt user-message is the shielded JSON only. */
public final class SummarizerRoleRequest {
    private final ShieldedContext context;
    private final PriorityResult deterministic;
    private final String eventStatus;
    private final Integer tickEventVersion;
    private final Integer currentEventVersion;
    private final Double systemConfidence;

    public SummarizerRoleRequest(ShieldedContext context, PriorityResult deterministic) {
        this(context, deterministic, "ACTIVE", 1, 1, null);
    }

    public SummarizerRoleRequest(
            ShieldedContext context,
            PriorityResult deterministic,
            String eventStatus,
            Integer tickEventVersion,
            Integer currentEventVersion,
            Double systemConfidence) {
        this.context = Objects.requireNonNull(context, "context");
        this.deterministic = Objects.requireNonNull(deterministic, "deterministic");
        this.eventStatus = eventStatus;
        this.tickEventVersion = tickEventVersion;
        this.currentEventVersion = currentEventVersion;
        this.systemConfidence = systemConfidence;
    }

    public ShieldedContext getContext() {
        return context;
    }

    public PriorityResult getDeterministic() {
        return deterministic;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public Integer getTickEventVersion() {
        return tickEventVersion;
    }

    public Integer getCurrentEventVersion() {
        return currentEventVersion;
    }

    public Double getSystemConfidence() {
        return systemConfidence;
    }

    public boolean injectionFlagged() {
        return context.isInjectionFlagged()
                || deterministic.getFeatures().isMaliciousInstruction();
    }
}
