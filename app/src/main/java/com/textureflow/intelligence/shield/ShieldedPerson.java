package com.textureflow.intelligence.shield;

public final class ShieldedPerson {
    private final String displayName;
    private final String relationship;

    public ShieldedPerson(String displayName, String relationship) {
        this.displayName = displayName == null ? "" : displayName;
        this.relationship = relationship == null ? "" : relationship;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRelationship() {
        return relationship;
    }
}
