package com.textureflow.intelligence.triage;

import java.util.Objects;

public final class AmbiguousCandidate {
    private final String personId;
    private final String displayName;

    public AmbiguousCandidate(String personId, String displayName) {
        this.personId = Objects.requireNonNull(personId, "personId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    public String getPersonId() { return personId; }
    public String getDisplayName() { return displayName; }
}
