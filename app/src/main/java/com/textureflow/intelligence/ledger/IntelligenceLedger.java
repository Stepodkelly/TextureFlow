package com.textureflow.intelligence.ledger;

import com.textureflow.intelligence.api.CapabilityProfile;

import java.util.concurrent.TimeUnit;

/**
 * Append-only intelligence metrics. Tick / assessment / role rows never store message bodies.
 * Proposal payload text is kept only until a terminal status.
 */
public interface IntelligenceLedger {
    long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(14);

    void recordTick(TickRecord tick);

    void recordAssessment(AssessmentRecord assessment);

    void recordRoleRun(RoleRunRecord roleRun);

    void upsertProposal(ProposalRecord proposal);

    /**
     * Moves a proposal to {@code newStatus}. Terminal statuses clear payload text.
     * Already-terminal proposals cannot change status.
     */
    ProposalRecord transitionProposal(String proposalId, ProposalStatus newStatus);

    /** {@code null} when no profile has been saved on this install. */
    CapabilityProfile loadCapabilityProfile();

    void saveCapabilityProfile(CapabilityProfile profile);

    /**
     * Drops tick/assessment/role metrics with {@code startedAt} before {@code cutoffMillis},
     * and terminal proposals whose {@code createdAt} is before the cutoff.
     * Callers should pass {@code now - RETENTION_MILLIS} for the 14-day window.
     */
    void purgeOlderThan(long cutoffMillis);
}
