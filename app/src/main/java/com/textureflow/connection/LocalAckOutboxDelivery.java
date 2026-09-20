package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;

/** Acknowledges the durable outbox locally without uploading to Convex. */
public final class LocalAckOutboxDelivery implements OutboxDelivery {
    @Override
    public void deliver(OutboxRecord record) {
        // Intentionally empty: stub mode keeps events on-device only.
    }
}
