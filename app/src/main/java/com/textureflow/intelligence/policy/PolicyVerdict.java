package com.textureflow.intelligence.policy;

import com.textureflow.intelligence.api.AttentionLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Gate output from doc §6.4. {@code accepted} is the only signal the UI should treat as live. */
public final class PolicyVerdict {
    private final boolean accepted;
    private final boolean capped;
    private final boolean stale;
    private final boolean dropped;
    private final List<String> reasons;
    private final AttentionLevel effectiveLevel;

    public PolicyVerdict(
            boolean accepted,
            boolean capped,
            boolean stale,
            boolean dropped,
            List<String> reasons,
            AttentionLevel effectiveLevel) {
        this.accepted = accepted;
        this.capped = capped;
        this.stale = stale;
        this.dropped = dropped;
        this.reasons = reasons == null || reasons.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(reasons));
        this.effectiveLevel = effectiveLevel;
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean capped() {
        return capped;
    }

    public boolean stale() {
        return stale;
    }

    public boolean dropped() {
        return dropped;
    }

    public List<String> reasons() {
        return reasons;
    }

    public AttentionLevel effectiveLevel() {
        return effectiveLevel;
    }

    public boolean hasReason(String ruleName) {
        return reasons.contains(ruleName);
    }
}
