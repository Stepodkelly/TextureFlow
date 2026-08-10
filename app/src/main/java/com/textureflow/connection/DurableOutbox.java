package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;

import java.util.List;

public interface DurableOutbox {
    List<OutboxRecord> claimBatch(int limit, long now, long leaseDurationMs);
    void acknowledge(long id);
    void release(long id, String error, long retryAt);
}
