package com.textureflow.intelligence.triage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AmbiguousIdentity implements IdentityResolution {
    private final String query;
    private final List<AmbiguousCandidate> candidates;

    public AmbiguousIdentity(String query, List<AmbiguousCandidate> candidates) {
        this.query = Objects.requireNonNull(query, "query");
        this.candidates = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(candidates, "candidates")));
    }

    @Override
    public IdentityKind getKind() {
        return IdentityKind.AMBIGUOUS;
    }

    @Override
    public double getImportance() {
        return 0.5;
    }

    public String getQuery() { return query; }
    public List<AmbiguousCandidate> getCandidates() { return candidates; }
}
