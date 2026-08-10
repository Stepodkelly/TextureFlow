package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;

public interface OutboxDelivery {
    void deliver(OutboxRecord record) throws Exception;

    default void afterAcknowledged(OutboxRecord record) {}
}
