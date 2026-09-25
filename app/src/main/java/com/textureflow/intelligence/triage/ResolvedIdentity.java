package com.textureflow.intelligence.triage;

import java.util.Objects;

public final class ResolvedIdentity implements IdentityResolution {
    private final String personId;
    private final String displayName;
    private final double importance;
    private final String relationship;
    private final String matchedAlias;

    public ResolvedIdentity(
            String personId,
            String displayName,
            double importance,
            String relationship,
            String matchedAlias) {
        this.personId = Objects.requireNonNull(personId, "personId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.importance = importance;
        this.relationship = relationship;
        this.matchedAlias = Objects.requireNonNull(matchedAlias, "matchedAlias");
    }

    @Override
    public IdentityKind getKind() {
        return IdentityKind.RESOLVED;
    }

    public String getPersonId() { return personId; }
    public String getDisplayName() { return displayName; }

    @Override
    public double getImportance() { return importance; }

    public String getRelationship() { return relationship; }
    public String getMatchedAlias() { return matchedAlias; }
}
