package com.textureflow.intelligence.triage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PersonAliasSeed {
    private final String personId;
    private final String displayName;
    private final double importance;
    private final String relationship;
    private final List<PersonAlias> aliases;

    public PersonAliasSeed(
            String personId,
            String displayName,
            double importance,
            String relationship,
            List<PersonAlias> aliases) {
        this.personId = Objects.requireNonNull(personId, "personId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.importance = importance;
        this.relationship = relationship;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(aliases, "aliases")));
    }

    public String getPersonId() { return personId; }
    public String getDisplayName() { return displayName; }
    public double getImportance() { return importance; }
    public String getRelationship() { return relationship; }
    public List<PersonAlias> getAliases() { return aliases; }
}
