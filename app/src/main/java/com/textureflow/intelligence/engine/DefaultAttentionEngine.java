package com.textureflow.intelligence.engine;

import com.textureflow.actions.ActionType;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionEngine;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.AttentionListener;
import com.textureflow.intelligence.api.Callback;
import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.DraftQuery;
import com.textureflow.intelligence.api.EventSignal;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.api.JobClass;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.api.SummaryQuery;
import com.textureflow.intelligence.api.SummaryResult;
import com.textureflow.intelligence.jobs.JobClassRegistry;
import com.textureflow.intelligence.ledger.AssessmentRecord;
import com.textureflow.intelligence.ledger.CouncilRole;
import com.textureflow.intelligence.ledger.IntelligenceLedger;
import com.textureflow.intelligence.ledger.ProposalRecord;
import com.textureflow.intelligence.ledger.ProposalStatus;
import com.textureflow.intelligence.ledger.RoleRunRecord;
import com.textureflow.intelligence.ledger.TickRecord;
import com.textureflow.intelligence.ledger.TickTrigger;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;
import com.textureflow.intelligence.policy.PolicyGate;
import com.textureflow.intelligence.policy.PolicyRequest;
import com.textureflow.intelligence.policy.PolicyVerdict;
import com.textureflow.intelligence.policy.ProposalBinding;
import com.textureflow.intelligence.policy.SchemaName;
import com.textureflow.intelligence.policy.SchemaValidator;
import com.textureflow.intelligence.shield.ContextShield;
import com.textureflow.intelligence.shield.ShieldEvent;
import com.textureflow.intelligence.shield.ShieldRequest;
import com.textureflow.intelligence.shield.ShieldedContext;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.IdentityKind;
import com.textureflow.intelligence.triage.IdentityResolution;
import com.textureflow.intelligence.triage.IdentityResolver;
import com.textureflow.intelligence.triage.PriorityAssessment;
import com.textureflow.intelligence.triage.PriorityFeatures;
import com.textureflow.intelligence.triage.PriorityResult;
import com.textureflow.intelligence.triage.ResolvedIdentity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Doc §5 tick FSM. Deterministic assessment is published before any model call.
 */
public final class DefaultAttentionEngine implements AttentionEngine, AutoCloseable {
    private final EventStore events;
    private final IntelligenceLedger ledger;
    private final ModelPort model;
    private final CapabilityProfile capability;
    private final ContextShield shield;
    private final PolicyGate policy;
    private final SchemaValidator schemas;
    private final IdentityResolver identities;
    private final JobClassRegistry jobs;
    private final EngineClock clock;
    private final TickScheduler scheduler;
    private final List<AttentionListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<OpenProposal>> openProposals = new LinkedHashMap<>();
    private final AtomicLong tickSeq = new AtomicLong();

    public DefaultAttentionEngine(EventStore events, IntelligenceLedger ledger) {
        this(
                events,
                ledger,
                new NoModelPort(),
                CapabilityProfiles.deterministicOnly(),
                new ContextShield(),
                new PolicyGate(),
                new SchemaValidator(),
                new IdentityResolver(),
                new JobClassRegistry(),
                EngineClock.SYSTEM,
                new TickScheduler());
    }

    public DefaultAttentionEngine(
            EventStore events,
            IntelligenceLedger ledger,
            ModelPort model,
            CapabilityProfile capability) {
        this(
                events,
                ledger,
                model,
                capability,
                new ContextShield(),
                new PolicyGate(),
                new SchemaValidator(),
                new IdentityResolver(),
                new JobClassRegistry(),
                EngineClock.SYSTEM,
                new TickScheduler());
    }

    public DefaultAttentionEngine(
            EventStore events,
            IntelligenceLedger ledger,
            ModelPort model,
            CapabilityProfile capability,
            ContextShield shield,
            PolicyGate policy,
            SchemaValidator schemas,
            IdentityResolver identities,
            JobClassRegistry jobs,
            EngineClock clock,
            TickScheduler scheduler) {
        this.events = Objects.requireNonNull(events, "events");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.model = model == null ? new NoModelPort() : model;
        this.capability = capability == null ? CapabilityProfiles.deterministicOnly() : capability;
        this.shield = Objects.requireNonNull(shield, "shield");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void onEvent(EventSignal signal) {
        Objects.requireNonNull(signal, "signal");
        scheduler.enqueue(signal, this::runTick);
    }

    @Override
    public void requestSummary(SummaryQuery query, Callback<SummaryResult> cb) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(cb, "cb");
        requireJobClass(JobClassRegistry.SUMMARY);
        scheduler.execute(() -> {
            try {
                cb.onResult(buildSummary(query));
            } catch (Exception error) {
                cb.onError(error);
            }
        });
    }

    @Override
    public void requestDraft(DraftQuery query, Callback<ProposalDraft> cb) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(cb, "cb");
        requireJobClass(JobClassRegistry.DRAFT);
        scheduler.execute(() -> {
            try {
                cb.onResult(buildDraft(query));
            } catch (Exception error) {
                cb.onError(error);
            }
        });
    }

    @Override
    public void addListener(AttentionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(AttentionListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public CapabilityProfile capability() {
        return capability;
    }

    public JobClass requireJobClass(String id) {
        return jobs.require(id);
    }

    public void awaitIdle(long timeoutMs) throws InterruptedException, TimeoutException {
        scheduler.awaitIdle(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdown();
    }

    private void runTick(EventSignal signal) {
        requireJobClass(JobClassRegistry.TRIAGE);
        long startedAt = clock.nowMillis();
        String tickId = nextTickId(startedAt);
        TickTrigger trigger = triggerFor(signal.getKind());

        if (signal.getKind() == EventSignal.Kind.REMOVED) {
            invalidateForEvent(signal.getEventId(), "EVENT_REMOVED");
        } else {
            invalidateIfVersionChanged(signal);
        }

        List<StoredEvent> live = events.getLiveForPerson(signal.getPersonId());
        StoredEvent lead = leadEvent(live, signal);
        if (lead == null && signal.getKind() != EventSignal.Kind.REMOVED) {
            StoredEvent stored = events.getByEventId(signal.getEventId());
            if (stored != null && stored.isLive()) {
                lead = stored;
                live = Collections.singletonList(stored);
            }
        }

        IdentityResolution identity = lead == null
                ? identities.resolveEvent(leadTriagePlaceholder(signal))
                : identities.resolveEvent(lead.toTriageEvent());
        PriorityResult deterministic = lead == null
                ? emptyPriority()
                : DeterministicTriage.assess(lead.toTriageEvent(), identity, startedAt);
        EscalationDecision decision = EscalationRules.decide(
                deterministic.getFeatures(), trigger, capability);

        ShieldedContext shielded = shield.shield(new ShieldRequest(
                signal.getPersonId(),
                displayName(identity, lead, signal),
                relationship(identity),
                lead == null ? "" : lead.getAppLabel(),
                "",
                toShieldEvents(live),
                startedAt));

        AttentionAssessment published = publishDeterministic(
                tickId, signal, lead, deterministic, decision, shielded);
        ModelOutcome modelOutcome = runCouncil(tickId, decision, shielded, lead, deterministic);
        if (modelOutcome != null) {
            published = publishMerged(tickId, signal, lead, deterministic, decision, modelOutcome);
        }

        ledger.recordTick(new TickRecord(
                tickId,
                trigger,
                signal.getPersonId(),
                signal.getPackageName(),
                startedAt,
                Math.max(0, clock.nowMillis() - startedAt),
                decision.ruleId(),
                capability.getTier(),
                published.getSource()));
        ledger.recordAssessment(AssessmentRecord.from(published, decision.ruleId()));
    }

    private AttentionAssessment publishDeterministic(
            String tickId,
            EventSignal signal,
            StoredEvent lead,
            PriorityResult deterministic,
            EscalationDecision decision,
            ShieldedContext shielded) {
        PriorityAssessment assessment = deterministic.getAssessment();
        AttentionLevel level = applyCap(assessment.getLevel(), decision);
        AttentionAssessment published = new AttentionAssessment(
                tickId,
                signal.getPersonId(),
                signal.getPackageName(),
                lead == null ? signal.getEventId() : lead.getEventId(),
                lead == null ? signal.getEventVersion() : lead.getEventVersion(),
                assessment.getScore(),
                level,
                assessment.getReason(),
                assessment.getScore(),
                null,
                decision.source());
        notifyAssessment(published);
        return published;
    }

    private AttentionAssessment publishMerged(
            String tickId,
            EventSignal signal,
            StoredEvent lead,
            PriorityResult deterministic,
            EscalationDecision decision,
            ModelOutcome modelOutcome) {
        ConfidenceMerger.Result merged = ConfidenceMerger.merge(
                deterministic.getAssessment(),
                deterministic.getFeatures(),
                modelOutcome.level,
                modelOutcome.confidence,
                modelOutcome.accepted);
        AttentionLevel level = applyCap(merged.getLevel(), decision);
        AssessmentSource source = modelOutcome.accepted
                ? AssessmentSource.ON_DEVICE_MODEL
                : AssessmentSource.DETERMINISTIC_FALLBACK;
        AttentionAssessment published = new AttentionAssessment(
                tickId,
                signal.getPersonId(),
                signal.getPackageName(),
                lead == null ? signal.getEventId() : lead.getEventId(),
                lead == null ? signal.getEventVersion() : lead.getEventVersion(),
                merged.getScore(),
                level,
                merged.getReason(),
                merged.getSystemConfidence(),
                merged.getModelConfidence(),
                source);
        notifyAssessment(published);
        return published;
    }

    private ModelOutcome runCouncil(
            String tickId,
            EscalationDecision decision,
            ShieldedContext shielded,
            StoredEvent lead,
            PriorityResult deterministic) {
        if (!decision.runsModel()) {
            return null;
        }
        long started = clock.nowMillis();
        boolean schemaValid = false;
        int inputTokens = shielded.inputTokens();
        int outputTokens = 0;
        try {
            model.load();
            ModelResponse response = model.generate(new ModelRequest(
                    shielded.toModelJson().toString(), 128, 0.2f));
            inputTokens = response.getInputTokens();
            outputTokens = response.getOutputTokens();
            PolicyRequest request = PolicyRequest.builder()
                    .schema(SchemaName.TRIAGE)
                    .modelJson(response.getText())
                    .injectionFlagged(
                            shielded.isInjectionFlagged()
                                    || deterministic.getFeatures().isMaliciousInstruction())
                    .eventStatus(lead == null ? StoredEvent.STATUS_ACTIVE : lead.getStatus())
                    .tickEventVersion(lead == null ? null : lead.getEventVersion())
                    .currentEventVersion(currentVersion(lead))
                    .proposedLevel(parseUrgency(response.getText()))
                    .systemConfidence(deterministic.getAssessment().getScore())
                    .build();
            PolicyVerdict verdict = policy.evaluate(request);
            if (verdict.accepted()) {
                JSONObject json = schemas.validate(SchemaName.TRIAGE, response.getText());
                schemaValid = true;
                AttentionLevel modelLevel = AttentionLevel.valueOf(json.getString("urgency"));
                if (verdict.capped() && verdict.effectiveLevel() != null) {
                    modelLevel = verdict.effectiveLevel();
                }
                return new ModelOutcome(modelLevel, json.getDouble("modelConfidence"), true);
            }
            return new ModelOutcome(null, null, false);
        } catch (Exception ignored) {
            return new ModelOutcome(null, null, false);
        } finally {
            ledger.recordRoleRun(new RoleRunRecord(
                    tickId,
                    CouncilRole.TRIAGE,
                    "triage.v1",
                    inputTokens,
                    outputTokens,
                    Math.max(0, clock.nowMillis() - started),
                    schemaValid,
                    Collections.emptyList()));
        }
    }

    private SummaryResult buildSummary(SummaryQuery query) {
        long now = clock.nowMillis();
        String tickId = nextTickId(now);
        List<StoredEvent> live = events.getLiveForPerson(query.getPersonId());
        StoredEvent lead = leadEvent(live, null);
        IdentityResolution identity = lead == null
                ? identities.resolveMention("")
                : identities.resolveEvent(lead.toTriageEvent());
        PriorityResult deterministic = lead == null
                ? emptyPriority()
                : DeterministicTriage.assess(lead.toTriageEvent(), identity, now);
        EscalationDecision decision = EscalationRules.decide(
                deterministic.getFeatures(), TickTrigger.USER_QUERY, capability);
        List<String> eventIds = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (StoredEvent event : live) {
            eventIds.add(event.getEventId());
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(event.getBody());
        }
        String summary = text.length() == 0
                ? "No recent messages."
                : trim(text.toString(), 240);
        AssessmentSource source = decision.source();
        ledger.recordTick(new TickRecord(
                tickId,
                TickTrigger.USER_QUERY,
                query.getPersonId(),
                lead == null ? "" : lead.getPackageName(),
                now,
                Math.max(0, clock.nowMillis() - now),
                decision.ruleId(),
                capability.getTier(),
                source));
        return new SummaryResult(
                summary,
                deterministic.getAssessment().getScore(),
                applyCap(deterministic.getAssessment().getLevel(), decision),
                deterministic.getAssessment().getReason(),
                intentFor(deterministic.getFeatures()),
                deterministic.getFeatures().getDirectRequest() >= 0.75d,
                Collections.emptyList(),
                source,
                eventIds,
                deterministic.getFeatures().isMaliciousInstruction(),
                decision.getRule() == EscalationDecision.Rule.A5 ? "model_unavailable" : null);
    }

    private ProposalDraft buildDraft(DraftQuery query) {
        long now = clock.nowMillis();
        StoredEvent stored = events.getByEventId(query.getEventId());
        if (stored == null || !stored.isLive()) {
            throw new IllegalStateException("Event is gone: " + query.getEventId());
        }
        IdentityResolution identity = identities.resolveEvent(stored.toTriageEvent());
        PriorityResult deterministic = DeterministicTriage.assess(
                stored.toTriageEvent(), identity, now);
        EscalationDecision decision = EscalationRules.decide(
                deterministic.getFeatures(), TickTrigger.DRAFT_REQUEST, capability);
        if (decision.forbidDrafts()) {
            throw new IllegalStateException("Drafts are forbidden for flagged events.");
        }
        String reply = query.getUserDraftText().isEmpty()
                ? query.getUserRequest()
                : query.getUserDraftText();
        if (reply.isEmpty()) {
            throw new IllegalStateException("A draft needs the user's words.");
        }
        String payloadHash = ProposalBinding.sha256Utf8(reply);
        String proposalId = "prop_" + UUID.randomUUID();
        ReplyTone tone = query.getPreferredTone() == null ? ReplyTone.NEUTRAL : query.getPreferredTone();
        ProposalDraft draft = new ProposalDraft(
                proposalId,
                stored.getEventId(),
                stored.getEventVersion(),
                stored.getPackageName(),
                stored.getPersonId(),
                stored.getSenderDisplayName(),
                ActionType.REPLY,
                reply,
                tone,
                "Reply to " + stored.getSenderDisplayName() + ": " + reply,
                AssessmentSource.DETERMINISTIC,
                deterministic.getAssessment().getScore(),
                Collections.emptyList(),
                payloadHash,
                now + 15 * 60_000L);
        ledger.upsertProposal(ProposalRecord.from(draft, ProposalStatus.OPEN, now));
        trackProposal(draft);
        return draft;
    }

    private void invalidateIfVersionChanged(EventSignal signal) {
        StoredEvent stored = events.getByEventId(signal.getEventId());
        List<OpenProposal> open = openProposals.getOrDefault(signal.getEventId(), Collections.emptyList());
        for (OpenProposal proposal : new ArrayList<>(open)) {
            boolean versionChanged = stored != null && stored.getEventVersion() != proposal.eventVersion;
            boolean signalChanged = signal.getEventVersion() != proposal.eventVersion;
            if (versionChanged || signalChanged) {
                invalidateProposal(proposal.proposalId, signal.getEventId(), "EVENT_VERSION_CHANGED");
            }
        }
    }

    private void invalidateForEvent(String eventId, String reason) {
        List<OpenProposal> open = openProposals.getOrDefault(eventId, Collections.emptyList());
        for (OpenProposal proposal : new ArrayList<>(open)) {
            invalidateProposal(proposal.proposalId, eventId, reason);
        }
    }

    private void invalidateProposal(String proposalId, String eventId, String reason) {
        try {
            ledger.transitionProposal(proposalId, ProposalStatus.INVALIDATED);
        } catch (RuntimeException ignored) {
            // Tests may track a proposal the ledger never saw.
        }
        List<OpenProposal> open = openProposals.get(eventId);
        if (open != null) {
            open.removeIf(item -> item.proposalId.equals(proposalId));
            if (open.isEmpty()) {
                openProposals.remove(eventId);
            }
        }
        for (AttentionListener listener : listeners) {
            listener.onProposalInvalidated(proposalId, reason);
        }
    }

    private void trackProposal(ProposalDraft draft) {
        openProposals
                .computeIfAbsent(draft.getEventId(), key -> new ArrayList<>())
                .add(new OpenProposal(draft.getProposalId(), draft.getEventVersion()));
    }

    private void notifyAssessment(AttentionAssessment assessment) {
        for (AttentionListener listener : listeners) {
            listener.onAssessment(assessment);
        }
    }

    private Integer currentVersion(StoredEvent lead) {
        if (lead == null) {
            return null;
        }
        StoredEvent current = events.getByEventId(lead.getEventId());
        return current == null ? lead.getEventVersion() : current.getEventVersion();
    }

    private static AttentionLevel applyCap(AttentionLevel level, EscalationDecision decision) {
        AttentionLevel cap = decision.capLevel();
        if (cap == null) {
            return level;
        }
        return level.ordinal() > cap.ordinal() ? cap : level;
    }

    private static StoredEvent leadEvent(List<StoredEvent> live, EventSignal signal) {
        if (signal != null) {
            for (StoredEvent event : live) {
                if (event.getEventId().equals(signal.getEventId())) {
                    return event;
                }
            }
        }
        return live.stream().max(Comparator.comparingLong(StoredEvent::getPostedAtMillis)).orElse(null);
    }

    private static List<ShieldEvent> toShieldEvents(List<StoredEvent> live) {
        List<ShieldEvent> out = new ArrayList<>();
        for (StoredEvent event : live) {
            out.add(event.toShieldEvent());
        }
        return out;
    }

    private static com.textureflow.intelligence.triage.TriageEvent leadTriagePlaceholder(EventSignal signal) {
        return new com.textureflow.intelligence.triage.TriageEvent(
                signal.getEventId(), signal.getPackageName(), "", null, null, "", signal.getPersonId());
    }

    private static PriorityResult emptyPriority() {
        return DeterministicTriage.assess(
                new com.textureflow.intelligence.triage.TriageEvent("", "", "", null, null, "", null),
                new com.textureflow.intelligence.triage.ProvisionalIdentity(
                        "person_unknown", "Unknown sender", 0.5d, ""),
                0L);
    }

    private static String displayName(
            IdentityResolution identity, StoredEvent lead, EventSignal signal) {
        if (identity instanceof ResolvedIdentity) {
            return ((ResolvedIdentity) identity).getDisplayName();
        }
        if (lead != null && !lead.getSenderDisplayName().isEmpty()) {
            return lead.getSenderDisplayName();
        }
        return signal.getPersonId();
    }

    private static String relationship(IdentityResolution identity) {
        if (identity instanceof ResolvedIdentity) {
            String value = ((ResolvedIdentity) identity).getRelationship();
            return value == null ? "" : value;
        }
        return identity.getKind() == IdentityKind.PROVISIONAL ? "" : "";
    }

    private static TickTrigger triggerFor(EventSignal.Kind kind) {
        if (kind == EventSignal.Kind.REMOVED) {
            return TickTrigger.EVENT_REMOVED;
        }
        if (kind == EventSignal.Kind.UPDATED) {
            return TickTrigger.EVENT_UPDATED;
        }
        return TickTrigger.EVENT_POSTED;
    }

    private static IntelligenceIntent intentFor(PriorityFeatures features) {
        if (features.isMaliciousInstruction()) {
            return IntelligenceIntent.UNKNOWN;
        }
        if (features.isPromotional()) {
            return IntelligenceIntent.PROMOTION;
        }
        if (features.getUrgencySignals() >= 0.8d && features.getDirectRequest() >= 0.75d) {
            return IntelligenceIntent.REQUEST_FOR_IMMEDIATE_ACTION;
        }
        if (features.getDirectRequest() >= 0.75d) {
            return IntelligenceIntent.REQUEST;
        }
        return IntelligenceIntent.INFORMATION;
    }

    private AttentionLevel parseUrgency(String json) {
        try {
            return AttentionLevel.valueOf(new JSONObject(json).getString("urgency"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String nextTickId(long now) {
        return "tick_" + Long.toString(now, 36) + "_" + tickSeq.incrementAndGet();
    }

    private static String trim(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private static final class OpenProposal {
        final String proposalId;
        final int eventVersion;

        OpenProposal(String proposalId, int eventVersion) {
            this.proposalId = proposalId;
            this.eventVersion = eventVersion;
        }
    }

    private static final class ModelOutcome {
        final AttentionLevel level;
        final Double confidence;
        final boolean accepted;

        ModelOutcome(AttentionLevel level, Double confidence, boolean accepted) {
            this.level = level;
            this.confidence = confidence;
            this.accepted = accepted;
        }
    }
}
