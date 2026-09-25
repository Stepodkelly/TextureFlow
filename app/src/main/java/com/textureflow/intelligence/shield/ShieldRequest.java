package com.textureflow.intelligence.shield;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Inputs for one {@link ContextShield} pass. */
public final class ShieldRequest {
    private final String personId;
    private final String displayName;
    private final String relationship;
    private final String appLabel;
    private final String userInstruction;
    private final List<ShieldEvent> events;
    private final long nowMillis;

    public ShieldRequest(
            String personId,
            String displayName,
            String relationship,
            String appLabel,
            String userInstruction,
            List<ShieldEvent> events,
            long nowMillis) {
        this.personId = personId == null ? "" : personId;
        this.displayName = displayName == null ? "" : displayName;
        this.relationship = relationship == null ? "" : relationship;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.userInstruction = userInstruction == null ? "" : userInstruction;
        this.events = events == null || events.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(events));
        this.nowMillis = nowMillis;
    }

    public String getPersonId() {
        return personId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRelationship() {
        return relationship;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public String getUserInstruction() {
        return userInstruction;
    }

    public List<ShieldEvent> getEvents() {
        return events;
    }

    public long getNowMillis() {
        return nowMillis;
    }
}
