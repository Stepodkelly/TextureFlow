package com.textureflow.intelligence.ledger;

import com.textureflow.intelligence.api.CapabilityProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Full JVM fake of {@link IntelligenceLedger} for other streams' unit tests. No Android. */
public final class InMemoryLedger implements IntelligenceLedger {
    private final Map<String, TickRecord> ticks = new LinkedHashMap<>();
    private final Map<String, AssessmentRecord> assessments = new LinkedHashMap<>();
    private final List<RoleRunRecord> roleRuns = new ArrayList<>();
    private final Map<String, ProposalRecord> proposals = new LinkedHashMap<>();
    private CapabilityProfile profile;

    @Override
    public synchronized void recordTick(TickRecord tick) {
        Objects.requireNonNull(tick, "tick");
        ticks.put(tick.getTickId(), tick);
    }

    @Override
    public synchronized void recordAssessment(AssessmentRecord assessment) {
        Objects.requireNonNull(assessment, "assessment");
        assessments.put(assessment.getTickId(), assessment);
    }

    @Override
    public synchronized void recordRoleRun(RoleRunRecord roleRun) {
        Objects.requireNonNull(roleRun, "roleRun");
        roleRuns.add(roleRun);
    }

    @Override
    public synchronized void upsertProposal(ProposalRecord proposal) {
        Objects.requireNonNull(proposal, "proposal");
        ProposalRecord existing = proposals.get(proposal.getProposalId());
        if (existing != null && existing.getStatus().isTerminal()) {
            throw new IllegalStateException("cannot upsert terminal proposal: " + proposal.getProposalId());
        }
        proposals.put(proposal.getProposalId(), proposal.withStatus(proposal.getStatus()));
    }

    @Override
    public synchronized ProposalRecord transitionProposal(String proposalId, ProposalStatus newStatus) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(newStatus, "newStatus");
        ProposalRecord existing = proposals.get(proposalId);
        if (existing == null) {
            throw new IllegalArgumentException("unknown proposal: " + proposalId);
        }
        if (existing.getStatus() == newStatus) {
            return existing;
        }
        if (existing.getStatus().isTerminal()) {
            throw new IllegalStateException("proposal already terminal: " + proposalId);
        }
        ProposalRecord updated = existing.withStatus(newStatus);
        proposals.put(proposalId, updated);
        return updated;
    }

    @Override
    public synchronized CapabilityProfile loadCapabilityProfile() {
        return profile;
    }

    @Override
    public synchronized void saveCapabilityProfile(CapabilityProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    @Override
    public synchronized void purgeOlderThan(long cutoffMillis) {
        List<String> expiredTickIds = ticks.values().stream()
                .filter(tick -> tick.getStartedAt() < cutoffMillis)
                .map(TickRecord::getTickId)
                .collect(Collectors.toList());
        for (String tickId : expiredTickIds) {
            ticks.remove(tickId);
            assessments.remove(tickId);
        }
        roleRuns.removeIf(run -> expiredTickIds.contains(run.getTickId()));
        proposals.values().removeIf(proposal ->
                proposal.getStatus().isTerminal() && proposal.getCreatedAt() < cutoffMillis);
    }

    public synchronized TickRecord getTick(String tickId) {
        return ticks.get(tickId);
    }

    public synchronized List<TickRecord> getTicks() {
        return Collections.unmodifiableList(new ArrayList<>(ticks.values()));
    }

    public synchronized AssessmentRecord getAssessment(String tickId) {
        return assessments.get(tickId);
    }

    public synchronized List<AssessmentRecord> getAssessments() {
        return Collections.unmodifiableList(new ArrayList<>(assessments.values()));
    }

    public synchronized List<RoleRunRecord> getRoleRuns() {
        return Collections.unmodifiableList(new ArrayList<>(roleRuns));
    }

    public synchronized List<RoleRunRecord> getRoleRuns(String tickId) {
        Objects.requireNonNull(tickId, "tickId");
        return roleRuns.stream()
                .filter(run -> tickId.equals(run.getTickId()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public synchronized ProposalRecord getProposal(String proposalId) {
        return proposals.get(proposalId);
    }

    public synchronized List<ProposalRecord> getProposals() {
        return Collections.unmodifiableList(new ArrayList<>(proposals.values()));
    }
}
