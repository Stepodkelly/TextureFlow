package com.textureflow.intelligence.triage;

/** Feature vector from {@code intelligence/src/types.ts} PriorityFeatures. */
public final class PriorityFeatures {
    private final double personImportance;
    private final double urgencySignals;
    private final double directRequest;
    private final double recency;
    private final double sourceRelevance;
    private final boolean promotional;
    private final boolean maliciousInstruction;

    public PriorityFeatures(
            double personImportance,
            double urgencySignals,
            double directRequest,
            double recency,
            double sourceRelevance,
            boolean promotional,
            boolean maliciousInstruction) {
        this.personImportance = personImportance;
        this.urgencySignals = urgencySignals;
        this.directRequest = directRequest;
        this.recency = recency;
        this.sourceRelevance = sourceRelevance;
        this.promotional = promotional;
        this.maliciousInstruction = maliciousInstruction;
    }

    public double getPersonImportance() { return personImportance; }
    public double getUrgencySignals() { return urgencySignals; }
    public double getDirectRequest() { return directRequest; }
    public double getRecency() { return recency; }
    public double getSourceRelevance() { return sourceRelevance; }
    public boolean isPromotional() { return promotional; }
    public boolean isMaliciousInstruction() { return maliciousInstruction; }
}
