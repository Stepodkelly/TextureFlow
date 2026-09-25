package com.textureflow.intelligence.triage;

/**
 * Tagged identity result. {@link IdentityKind#AMBIGUOUS} has no person importance
 * in TypeScript; scoring treats it as 0.5.
 */
public interface IdentityResolution {
    IdentityKind getKind();

    /**
     * Importance used by {@link DeterministicTriage#assess}. Ambiguous identities
     * report 0.5 to match {@code identity.kind === "AMBIGUOUS" ? 0.5 : identity.importance}.
     */
    double getImportance();
}
