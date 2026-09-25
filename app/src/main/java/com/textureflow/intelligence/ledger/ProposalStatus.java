package com.textureflow.intelligence.ledger;

/** Proposal lifecycle. Terminal statuses must drop payload text. */
public enum ProposalStatus {
    OPEN,
    CONFIRMED,
    EXPIRED,
    STALE,
    INVALIDATED;

    public boolean isTerminal() {
        return this != OPEN;
    }
}
