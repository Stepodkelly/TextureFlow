package com.textureflow.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.AttentionListener;
import com.textureflow.intelligence.api.EventSignal;
import com.textureflow.intelligence.engine.DefaultAttentionEngine;
import com.textureflow.intelligence.engine.InMemoryEventStore;
import com.textureflow.intelligence.engine.PersonKeys;
import com.textureflow.intelligence.engine.StoredEvent;
import com.textureflow.intelligence.ledger.InMemoryLedger;
import com.textureflow.intelligence.model.NoModelPort;
import com.textureflow.intelligence.triage.DeterministicTriage;
import com.textureflow.intelligence.triage.IdentityResolver;
import com.textureflow.intelligence.triage.PriorityResult;
import com.textureflow.intelligence.triage.TriageEvent;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

public final class NotificationNormalizerTriageTest {
    private static final long NOW = 1_775_719_920_000L;
    private static final long POSTED = NOW - 120_000L;

    @Test
    public void priorityUsesDeterministicTriageIdentity() {
        NotificationNormalizer.Priority scored = NotificationNormalizer.computePriority(
                "I'm downstairs. The door is locked.",
                "Sam",
                "com.whatsapp",
                POSTED,
                NOW);
        TriageEvent event = TriageEvent.atMillis(
                "", "com.whatsapp", "I'm downstairs. The door is locked.", POSTED, "Sam", null);
        PriorityResult expected = DeterministicTriage.assess(
                event, new IdentityResolver().resolveEvent(event), NOW);
        assertEquals(expected.getAssessment().getLevel().name(), scored.level);
        assertEquals(expected.getAssessment().getScore(), scored.score, 0.0);
        assertEquals(expected.getAssessment().getReason(), scored.reason);
        assertNotEquals("NORMAL", scored.level);
    }

    @Test
    public void oneArgPriorityStillGoesThroughTriage() {
        NotificationNormalizer.Priority scored = NotificationNormalizer.priority(
                "Limited offer: 50% off today. Shop now or unsubscribe.");
        assertEquals(AttentionLevel.LOW.name(), scored.level);
    }

    @Test
    public void listenerPathProducesSameLevelAsEngine() throws Exception {
        String body = "Can you come downstairs? I'm locked out.";
        String sender = "Sam";
        String pkg = "com.whatsapp";
        NotificationNormalizer.Priority listener = NotificationNormalizer.computePriority(
                body, sender, pkg, POSTED, NOW);

        String personId = PersonKeys.resolve(sender, pkg);
        InMemoryEventStore store = new InMemoryEventStore();
        store.put(new StoredEvent("evt_p", 1, personId, pkg, "WhatsApp", sender, body, POSTED, "ACTIVE"));
        AtomicReference<AttentionAssessment> published = new AtomicReference<>();
        try (DefaultAttentionEngine engine = new DefaultAttentionEngine(
                store, new InMemoryLedger(), new NoModelPort(),
                com.textureflow.intelligence.engine.CapabilityProfiles.deterministicOnly(),
                new com.textureflow.intelligence.shield.ContextShield(),
                new com.textureflow.intelligence.policy.PolicyGate(),
                new com.textureflow.intelligence.policy.SchemaValidator(),
                new IdentityResolver(),
                new com.textureflow.intelligence.jobs.JobClassRegistry(),
                () -> NOW,
                new com.textureflow.intelligence.engine.TickScheduler())) {
            engine.addListener(new AttentionListener() {
                @Override
                public void onAssessment(AttentionAssessment assessment) {
                    published.set(assessment);
                }

                @Override
                public void onProposalInvalidated(String proposalId, String reason) {
                }
            });
            engine.onEvent(new EventSignal(EventSignal.Kind.POSTED, "evt_p", 1, personId, pkg));
            engine.awaitIdle(2_000);
        }
        assertEquals(listener.level, published.get().getLevel().name());
    }

    @Test
    public void attentionSignalUsesResolvedPersonId() {
        StoredNotificationEvent stored = new StoredNotificationEvent(
                "evt_s", "dev", "key", "com.whatsapp", "WhatsApp", "Sam", "Sam",
                "hello", POSTED, NOW, 2, "ACTIVE",
                Collections.singleton("REPLY"), "hash", "fp", 0.5, "NORMAL", "reason");
        EventSignal signal = NotificationRuntime.attentionSignal(stored, EventSignal.Kind.UPDATED);
        assertEquals(EventSignal.Kind.UPDATED, signal.getKind());
        assertEquals("evt_s", signal.getEventId());
        assertEquals(2, signal.getEventVersion());
        assertEquals("person_sam", signal.getPersonId());
        assertEquals("com.whatsapp", signal.getPackageName());
    }
}
