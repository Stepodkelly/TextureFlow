package com.textureflow.intelligence.triage;

import java.util.Objects;

public final class ProvisionalIdentity implements IdentityResolution {
    private final String personId;
    private final String displayName;
    private final double importance;
    private final String matchedAlias;

    public ProvisionalIdentity(
            String personId,
            String displayName,
            double importance,
            String matchedAlias) {
        this.personId = Objects.requireNonNull(personId, "personId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.importance = importance;
        this.matchedAlias = Objects.requireNonNull(matchedAlias, "matchedAlias");
    }

    @Override
    public IdentityKind getKind() {
        return IdentityKind.PROVISIONAL;
    }

    public String getPersonId() { return personId; }
    public String getDisplayName() { return displayName; }

    @Override
    public double getImportance() { return importance; }

    public String getMatchedAlias() { return matchedAlias; }
}
