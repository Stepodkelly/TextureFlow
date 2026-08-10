package com.textureflow.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.data.OutboxRecord;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class OutboxCoordinatorTest {
    @Test
    public void failureReleasesWithoutAcknowledgingThenRetries() {
        FakeOutbox outbox = new FakeOutbox();
        FakeDelivery delivery = new FakeDelivery();
        delivery.fail = true;
        OutboxCoordinator coordinator = coordinator(outbox, delivery);

        OutboxCoordinator.DrainResult failed = coordinator.drain(1_000L, 0.5);
        assertEquals(1, failed.released());
        assertFalse(outbox.acknowledged);
        assertEquals(2_000L, outbox.availableAt);

        delivery.fail = false;
        assertEquals(0, coordinator.drain(1_999L, 0.5).claimed());
        assertEquals(1, coordinator.drain(2_000L, 0.5).acknowledged());
        assertTrue(outbox.acknowledged);
    }

    @Test
    public void expiredLeaseMakesInterruptedDeliveryClaimableAgain() {
        FakeOutbox outbox = new FakeOutbox();
        assertEquals(1, outbox.claimBatch(1, 1_000L, 10_000L).size());
        OutboxCoordinator coordinator = coordinator(outbox, new FakeDelivery());
        assertEquals(0, coordinator.drain(10_999L, 0.5).claimed());
        assertEquals(1, coordinator.drain(11_000L, 0.5).acknowledged());
    }

    @Test
    public void remoteSuccessWithLocalAckFailureIsRetriedIdempotently() {
        FakeOutbox outbox = new FakeOutbox();
        outbox.failFirstAcknowledge = true;
        FakeDelivery delivery = new FakeDelivery();
        OutboxCoordinator coordinator = coordinator(outbox, delivery);

        assertEquals(1, coordinator.drain(1_000L, 0.5).released());
        assertEquals(1, coordinator.drain(2_000L, 0.5).acknowledged());
        assertEquals(2, delivery.deliveries);
        assertEquals(1, delivery.acknowledgements);
    }

    private static OutboxCoordinator coordinator(FakeOutbox outbox, FakeDelivery delivery) {
        return new OutboxCoordinator(
                outbox, delivery, new BackoffPolicy(1_000L, 10_000L, 0), 1, 10_000L);
    }

    private static final class FakeDelivery implements OutboxDelivery {
        boolean fail;
        int deliveries;
        int acknowledgements;

        @Override
        public void deliver(OutboxRecord record) throws Exception {
            deliveries++;
            if (fail) throw new Exception("network unavailable");
        }

        @Override
        public void afterAcknowledged(OutboxRecord record) {
            acknowledgements++;
        }
    }

    private static final class FakeOutbox implements DurableOutbox {
        final OutboxRecord source = new OutboxRecord(
                1, "event:1", "EVENT_UPSERT", "event-1", "{}", 0);
        boolean inFlight;
        boolean acknowledged;
        boolean failFirstAcknowledge;
        int attempts;
        long availableAt;
        long leaseUntil;

        @Override
        public List<OutboxRecord> claimBatch(int limit, long now, long leaseDurationMs) {
            if (acknowledged || now < availableAt || (inFlight && now < leaseUntil)) {
                return Collections.emptyList();
            }
            inFlight = true;
            attempts++;
            leaseUntil = now + leaseDurationMs;
            return List.of(new OutboxRecord(
                    source.getId(), source.getOperationKey(), source.getKind(),
                    source.getAggregateId(), source.getPayload(), attempts));
        }

        @Override
        public void acknowledge(long id) {
            if (failFirstAcknowledge) {
                failFirstAcknowledge = false;
                throw new IllegalStateException("database busy");
            }
            acknowledged = true;
            inFlight = false;
        }

        @Override
        public void release(long id, String error, long retryAt) {
            inFlight = false;
            availableAt = retryAt;
            leaseUntil = 0;
        }
    }
}
