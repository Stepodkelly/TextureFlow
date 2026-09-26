package com.textureflow.intelligence.roles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.Callback;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.DraftQuery;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.api.SummaryQuery;
import com.textureflow.intelligence.api.SummaryResult;
import com.textureflow.intelligence.engine.CapabilityProfiles;
import com.textureflow.intelligence.engine.DefaultAttentionEngine;
import com.textureflow.intelligence.engine.InMemoryEventStore;
import com.textureflow.intelligence.engine.StoredEvent;
import com.textureflow.intelligence.ledger.CouncilRole;
import com.textureflow.intelligence.ledger.InMemoryLedger;
import com.textureflow.intelligence.ledger.RoleRunRecord;
import com.textureflow.intelligence.model.NoModelPort;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CouncilRolesEngineTest {
    private static final long NOW = 1_775_719_920_000L;
    private DefaultAttentionEngine engine;

    @After
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    public void buildSummaryUsesSummarizerRoleAndRecordsLedger() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_sum", "person_sam", "Sam", "Dinner at nine?"));
        InMemoryLedger ledger = new InMemoryLedger();
        FakeModelPort port = new FakeModelPort(""
                + "{\"summary\":\"Sam asked about dinner at nine.\","
                + "\"priorityScore\":0.6,\"priorityLevel\":\"NORMAL\","
                + "\"priorityReason\":\"A recent question.\","
                + "\"intent\":\"QUESTION\",\"requiresResponse\":true,"
                + "\"ambiguities\":[]}");
        engine = new DefaultAttentionEngine(
                store, ledger, port, CapabilityProfiles.onDevice(CapabilityTier.T2));
        ResultCallback<SummaryResult> cb = new ResultCallback<>();
        long started = System.nanoTime();
        engine.requestSummary(new SummaryQuery("person_sam", "what needs me?", false), cb);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 200);
        engine.awaitIdle(2_000);
        assertEquals("Sam asked about dinner at nine.", cb.value.getSummary());
        assertEquals(AssessmentSource.ON_DEVICE_MODEL, cb.value.getSource());
        assertEquals(1, port.generates);
        List<RoleRunRecord> runs = ledger.getRoleRuns();
        assertEquals(1, runs.size());
        assertEquals(CouncilRole.SUMMARIZER, runs.get(0).getRole());
        assertEquals(SummarizerRoleResult.PROMPT_VERSION, runs.get(0).getPromptVersion());
        assertTrue(runs.get(0).isSchemaValid());
    }

    @Test
    public void t0AndNoModelPortStayOnDeterministicFallback() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_sum", "person_sam", "Sam", "Dinner at nine?"));
        FakeModelPort port = new FakeModelPort("{\"summary\":\"should not run\"}");
        engine = new DefaultAttentionEngine(
                store, new InMemoryLedger(), port, CapabilityProfiles.deterministicOnly());
        ResultCallback<SummaryResult> cb = new ResultCallback<>();
        engine.requestSummary(new SummaryQuery("person_sam", "what needs me?", false), cb);
        engine.awaitIdle(2_000);
        assertEquals(0, port.generates);
        assertTrue(cb.value.getSummary().contains("Dinner"));

        engine.close();
        engine = new DefaultAttentionEngine(
                store,
                new InMemoryLedger(),
                new NoModelPort(),
                CapabilityProfiles.onDevice(CapabilityTier.T2));
        ResultCallback<SummaryResult> again = new ResultCallback<>();
        engine.requestSummary(new SummaryQuery("person_sam", "what needs me?", false), again);
        engine.awaitIdle(2_000);
        assertTrue(again.value.getSummary().contains("Dinner"));
    }

    @Test
    public void buildDraftUsesLiteralWordsAndDoesNotWriteRoleRuns() throws Exception {
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(event("evt_draft", "person_sam", "Sam", "Can you come down?"));
        InMemoryLedger ledger = new InMemoryLedger();
        FakeModelPort port = new FakeModelPort(""
                + "{\"text\":\"I will be there at 9.\",\"tone\":\"WARM\","
                + "\"confidence\":0.9,\"ambiguities\":[]}");
        engine = new DefaultAttentionEngine(
                store, ledger, port, CapabilityProfiles.onDevice(CapabilityTier.T2));
        ResultCallback<ProposalDraft> cb = new ResultCallback<>();
        engine.requestDraft(
                new DraftQuery("evt_draft", "tell him I'm coming", "I'm coming", ReplyTone.DIRECT),
                cb);
        engine.awaitIdle(2_000);
        assertEquals("I'm coming", cb.value.getReplyText());
        assertEquals(0, port.generates);
        assertTrue(ledger.getRoleRuns().isEmpty());
    }

    private static StoredEvent event(String eventId, String personId, String sender, String body) {
        return new StoredEvent(
                eventId, 1, personId, "com.whatsapp", "WhatsApp", sender, body, NOW - 60_000L, "ACTIVE");
    }

    private static final class ResultCallback<T> implements Callback<T> {
        volatile T value;

        @Override
        public void onResult(T value) {
            this.value = value;
        }

        @Override
        public void onError(Exception error) {
            throw new AssertionError(error);
        }
    }
}
