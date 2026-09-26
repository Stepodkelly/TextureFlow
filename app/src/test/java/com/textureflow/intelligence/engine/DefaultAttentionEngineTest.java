package com.textureflow.intelligence.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.AttentionListener;
import com.textureflow.intelligence.api.Callback;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.DraftQuery;
import com.textureflow.intelligence.api.EventSignal;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.api.SummaryQuery;
import com.textureflow.intelligence.api.SummaryResult;
import com.textureflow.intelligence.ledger.InMemoryLedger;
import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.IdentityResolver;
import com.textureflow.intelligence.triage.PriorityResult;
import com.textureflow.intelligence.triage.TriageEvent;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultAttentionEngineTest {
    private static final long NOW = 1_775_719_920_000L;
    private DefaultAttentionEngine engine;

    @After
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    public void publishesDeterministicAssessmentImmediately() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_1", "person_sam", "Sam", "I'm downstairs. The door is locked.", 1));
        RecordingListener listener = new RecordingListener();
        engine = newEngine(store, new NoModelPort(), CapabilityProfiles.deterministicOnly());
        engine.addListener(listener);

        engine.onEvent(new EventSignal(EventSignal.Kind.POSTED, "evt_1", 1, "person_sam", "com.whatsapp"));
        engine.awaitIdle(2_000);

        assertEquals(1, listener.assessments.size());
        AttentionAssessment assessment = listener.assessments.get(0);
        assertEquals(AttentionLevel.URGENT, assessment.getLevel());
        assertEquals(AssessmentSource.DETERMINISTIC, assessment.getSource());
        assertEquals("person_sam", assessment.getPersonId());
        assertEquals("triage", engine.requireJobClass("triage").id());
    }

    @Test
    public void listenerPathLevelMatchesEngine() throws Exception {
        String body = "Can you come downstairs? I'm locked out.";
        String sender = "Sam";
        String pkg = "com.whatsapp";
        long posted = NOW - 120_000L;
        TriageEvent triage = TriageEvent.atMillis("evt_parity", pkg, body, posted, sender, "person_sam");
        PriorityResult expected = DeterministicTriage.assess(
                triage, new IdentityResolver().resolveEvent(triage), NOW);

        InMemoryEventStore store = new InMemoryEventStore();
        store.put(new StoredEvent(
                "evt_parity", 1, "person_sam", pkg, "WhatsApp", sender, body, posted, "ACTIVE"));
        RecordingListener listener = new RecordingListener();
        engine = new DefaultAttentionEngine(
                store,
                new InMemoryLedger(),
                new NoModelPort(),
                CapabilityProfiles.deterministicOnly(),
                new com.textureflow.intelligence.shield.ContextShield(),
                new com.textureflow.intelligence.policy.PolicyGate(),
                new com.textureflow.intelligence.policy.SchemaValidator(),
                new IdentityResolver(),
                new com.textureflow.intelligence.jobs.JobClassRegistry(),
                () -> NOW,
                new TickScheduler());
        engine.addListener(listener);
        engine.onEvent(new EventSignal(EventSignal.Kind.POSTED, "evt_parity", 1, "person_sam", pkg));
        engine.awaitIdle(2_000);

        assertEquals(expected.getAssessment().getLevel(), listener.assessments.get(0).getLevel());
        assertEquals(expected.getAssessment().getReason(), listener.assessments.get(0).getReason());
    }

    @Test
    public void onEventDoesNotWaitForSlowModel() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        String alex = PersonKeys.resolve("Alex", "com.whatsapp");
        store.put(event("evt_slow", alex, "Alex", "hey are you around later", 1));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger generates = new AtomicInteger();
        ModelPort slow = new ModelPort() {
            @Override public void load() {}
            @Override
            public ModelResponse generate(ModelRequest request) throws Exception {
                generates.incrementAndGet();
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) {
                    fail("model was not released");
                }
                return new ModelResponse(
                        "{\"intent\":\"QUESTION\",\"requiresResponse\":true,\"urgency\":\"NORMAL\","
                                + "\"reason\":\"later\",\"modelConfidence\":0.4}",
                        10, 8, 5);
            }
            @Override public void unload() {}
            @Override public int tokenCount(String text) { return 1; }
            @Override public String runtimeName() { return "slow"; }
        };
        RecordingListener listener = new RecordingListener();
        engine = newEngine(store, slow, CapabilityProfiles.onDevice(CapabilityTier.T2));
        engine.addListener(listener);

        long started = System.nanoTime();
        engine.onEvent(new EventSignal(EventSignal.Kind.POSTED, "evt_slow", 1, alex, "com.whatsapp"));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue("onEvent blocked the caller for " + elapsedMs + "ms", elapsedMs < 200);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertTrue("deterministic result must publish before the model returns",
                listener.assessments.size() >= 1);
        release.countDown();
        engine.awaitIdle(2_000);
        assertTrue(generates.get() >= 1);
        assertTrue(listener.assessments.size() >= 1);
    }

    @Test
    public void modelUrgentWithoutSignalsIsCapped() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_hello", PersonKeys.resolve("Alex", "org.telegram.messenger"),
                "Alex", "hello there", 1));
        ModelPort urgent = fixedJson(
                "{\"intent\":\"REQUEST\",\"requiresResponse\":true,\"urgency\":\"URGENT\","
                        + "\"reason\":\"model spam\",\"modelConfidence\":0.99}");
        RecordingListener listener = new RecordingListener();
        engine = newEngine(store, urgent, CapabilityProfiles.onDevice(CapabilityTier.T2));
        engine.addListener(listener);
        engine.onEvent(new EventSignal(
                EventSignal.Kind.POSTED, "evt_hello", 1,
                PersonKeys.resolve("Alex", "org.telegram.messenger"),
                "org.telegram.messenger"));
        engine.awaitIdle(2_000);
        AttentionAssessment last = listener.assessments.get(listener.assessments.size() - 1);
        assertTrue(last.getLevel() != AttentionLevel.URGENT);
        assertEquals(AttentionLevel.IMPORTANT, last.getLevel());
    }

    @Test
    public void removalInvalidatesOpenProposal() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_draft", "person_sam", "Sam", "Can you come down?", 3));
        RecordingListener listener = new RecordingListener();
        engine = newEngine(store, new NoModelPort(), CapabilityProfiles.deterministicOnly());
        engine.addListener(listener);

        ResultCallback<ProposalDraft> draft = new ResultCallback<>();
        engine.requestDraft(new DraftQuery("evt_draft", "tell him I'm coming", "I'm coming", ReplyTone.DIRECT), draft);
        engine.awaitIdle(2_000);
        assertTrue(draft.value != null);

        store.put(new StoredEvent(
                "evt_draft", 4, "person_sam", "com.whatsapp", "WhatsApp", "Sam",
                "Can you come down?", NOW, StoredEvent.STATUS_REMOVED));
        engine.onEvent(new EventSignal(EventSignal.Kind.REMOVED, "evt_draft", 4, "person_sam", "com.whatsapp"));
        engine.awaitIdle(2_000);

        assertEquals(1, listener.invalidations.size());
        assertEquals(draft.value.getProposalId(), listener.invalidations.get(0).proposalId);
        assertEquals("EVENT_REMOVED", listener.invalidations.get(0).reason);
    }

    @Test
    public void versionChangeInvalidatesOpenProposal() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_ver", "person_sam", "Sam", "Can you pick me up?", 1));
        RecordingListener listener = new RecordingListener();
        engine = newEngine(store, new NoModelPort(), CapabilityProfiles.deterministicOnly());
        engine.addListener(listener);

        ResultCallback<ProposalDraft> draft = new ResultCallback<>();
        engine.requestDraft(new DraftQuery("evt_ver", "yes", "On my way", ReplyTone.DIRECT), draft);
        engine.awaitIdle(2_000);

        store.put(event("evt_ver", "person_sam", "Sam", "Can you pick me up now?", 2));
        engine.onEvent(new EventSignal(EventSignal.Kind.UPDATED, "evt_ver", 2, "person_sam", "com.whatsapp"));
        engine.awaitIdle(2_000);

        assertEquals("EVENT_VERSION_CHANGED", listener.invalidations.get(0).reason);
        assertEquals(draft.value.getProposalId(), listener.invalidations.get(0).proposalId);
    }

    @Test
    public void coalescesToNewestSignalPerPerson() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_a", "person_sam", "Sam", "first", 1));
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        EventStore blocking = new EventStore() {
            @Override
            public List<StoredEvent> getLiveForPerson(String personId) {
                started.countDown();
                try {
                    hold.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return store.getLiveForPerson(personId);
            }

            @Override
            public StoredEvent getByEventId(String eventId) {
                return store.getByEventId(eventId);
            }
        };
        RecordingListener listener = new RecordingListener();
        engine = newEngine(blocking, new NoModelPort(), CapabilityProfiles.deterministicOnly());
        engine.addListener(listener);
        engine.onEvent(new EventSignal(EventSignal.Kind.POSTED, "evt_a", 1, "person_sam", "com.whatsapp"));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        store.put(event("evt_b", "person_sam", "Sam", "second", 1));
        store.put(event("evt_c", "person_sam", "Sam", "third", 1));
        engine.onEvent(new EventSignal(EventSignal.Kind.UPDATED, "evt_b", 1, "person_sam", "com.whatsapp"));
        engine.onEvent(new EventSignal(EventSignal.Kind.UPDATED, "evt_c", 1, "person_sam", "com.whatsapp"));
        hold.countDown();
        engine.awaitIdle(2_000);
        List<String> leads = new ArrayList<>();
        for (AttentionAssessment assessment : listener.assessments) {
            leads.add(assessment.getLeadEventId());
        }
        assertTrue(leads.contains("evt_a"));
        assertTrue(leads.contains("evt_c"));
        assertTrue(!leads.contains("evt_b"));
    }

    @Test
    public void rejectsUnknownJobClass() {
        engine = newEngine(new InMemoryEventStore(), new NoModelPort(), CapabilityProfiles.deterministicOnly());
        try {
            engine.requireJobClass("thread_catch_up_v1");
            fail("unknown job class should be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unknown job class"));
        }
        assertSame(
                engine.requireJobClass("triage"),
                engine.requireJobClass("triage"));
    }

    @Test
    public void requestSummaryUsesRegistryAndDoesNotBlock() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_sum", "person_sam", "Sam", "Dinner at nine?", 1));
        engine = newEngine(store, new NoModelPort(), CapabilityProfiles.deterministicOnly());
        ResultCallback<SummaryResult> cb = new ResultCallback<>();
        long started = System.nanoTime();
        engine.requestSummary(new SummaryQuery("person_sam", "what needs me?", false), cb);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 200);
        engine.awaitIdle(2_000);
        assertTrue(cb.value != null);
        assertTrue(cb.value.getSummary().contains("Dinner"));
    }

    private DefaultAttentionEngine newEngine(
            EventStore store,
            ModelPort model,
            com.textureflow.intelligence.api.CapabilityProfile capability) {
        return new DefaultAttentionEngine(store, new InMemoryLedger(), model, capability);
    }

    private static StoredEvent event(
            String eventId, String personId, String sender, String body, int version) {
        return new StoredEvent(
                eventId, version, personId, "com.whatsapp", "WhatsApp", sender, body, NOW - 60_000L, "ACTIVE");
    }

    private static ModelPort fixedJson(String json) {
        return new ModelPort() {
            @Override public void load() {}
            @Override
            public ModelResponse generate(ModelRequest request) {
                return new ModelResponse(json, 8, 8, 2);
            }
            @Override public void unload() {}
            @Override public int tokenCount(String text) { return 1; }
            @Override public String runtimeName() { return "fixed"; }
        };
    }

    private static final class RecordingListener implements AttentionListener {
        final List<AttentionAssessment> assessments = new CopyOnWriteArrayList<>();
        final List<Invalidation> invalidations = new CopyOnWriteArrayList<>();

        @Override
        public void onAssessment(AttentionAssessment assessment) {
            assessments.add(assessment);
        }

        @Override
        public void onProposalInvalidated(String proposalId, String reason) {
            invalidations.add(new Invalidation(proposalId, reason));
        }
    }

    private static final class Invalidation {
        final String proposalId;
        final String reason;

        Invalidation(String proposalId, String reason) {
            this.proposalId = proposalId;
            this.reason = reason;
        }
    }

    private static final class ResultCallback<T> implements Callback<T> {
        volatile T value;
        volatile Exception error;

        @Override
        public void onResult(T value) {
            this.value = value;
        }

        @Override
        public void onError(Exception error) {
            this.error = error;
        }
    }
}
