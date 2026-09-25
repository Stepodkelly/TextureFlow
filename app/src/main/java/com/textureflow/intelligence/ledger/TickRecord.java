package com.textureflow.intelligence.ledger;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.CapabilityTier;

import java.util.Objects;

/** Ledger row for one attention tick. Metrics only — no message body. */
public final class TickRecord {
    private final String tickId;
    private final TickTrigger trigger;
    private final String personId;
    private final String packageName;
    private final long startedAt;
    private final long durationMs;
    private final String escalationRule;
    private final CapabilityTier tier;
    private final AssessmentSource source;

    public TickRecord(
            String tickId,
            TickTrigger trigger,
            String personId,
            String packageName,
            long startedAt,
            long durationMs,
            String escalationRule,
            CapabilityTier tier,
            AssessmentSource source) {
        this.tickId = Objects.requireNonNull(tickId, "tickId");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.personId = personId == null ? "" : personId;
        this.packageName = packageName == null ? "" : packageName;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.escalationRule = escalationRule == null ? "" : escalationRule;
        this.tier = Objects.requireNonNull(tier, "tier");
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getTickId() { return tickId; }
    public TickTrigger getTrigger() { return trigger; }
    public String getPersonId() { return personId; }
    public String getPackageName() { return packageName; }
    public long getStartedAt() { return startedAt; }
    public long getDurationMs() { return durationMs; }
    public String getEscalationRule() { return escalationRule; }
    public CapabilityTier getTier() { return tier; }
    public AssessmentSource getSource() { return source; }
}
