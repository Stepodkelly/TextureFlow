package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;

import java.util.List;

public final class OutboxCoordinator {
    public record DrainResult(int claimed, int acknowledged, int released) {}

    private final DurableOutbox outbox;
    private final OutboxDelivery delivery;
    private final BackoffPolicy backoff;
    private final int batchSize;
    private final long leaseDurationMs;

    public OutboxCoordinator(
            DurableOutbox outbox,
            OutboxDelivery delivery,
            BackoffPolicy backoff,
            int batchSize,
            long leaseDurationMs) {
        this.outbox = outbox;
        this.delivery = delivery;
        this.backoff = backoff;
        this.batchSize = Math.max(1, Math.min(25, batchSize));
        this.leaseDurationMs = Math.max(10_000L, leaseDurationMs);
    }

    public DrainResult drain(long now, double randomUnit) {
        List<OutboxRecord> records = outbox.claimBatch(batchSize, now, leaseDurationMs);
        int acknowledged = 0;
        int released = 0;
        for (OutboxRecord record : records) {
            try {
                delivery.deliver(record);
                outbox.acknowledge(record.getId());
                acknowledged++;
                try {
                    delivery.afterAcknowledged(record);
                } catch (RuntimeException ignored) {
                    // Delivery and durable acknowledgement are complete; cleanup is best effort.
                }
            } catch (Exception failure) {
                long retryAt = now + backoff.delayMillis(record.getAttempts(), randomUnit);
                try {
                    outbox.release(record.getId(), safeMessage(failure), retryAt);
                } catch (RuntimeException ignored) {
                    // The unacknowledged lease remains durable and becomes claimable after expiry.
                }
                released++;
            }
        }
        return new DrainResult(records.size(), acknowledged, released);
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) return failure.getClass().getSimpleName();
        return message.length() <= 400 ? message : message.substring(0, 400);
    }
}
