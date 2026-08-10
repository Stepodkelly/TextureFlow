package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;
import com.textureflow.data.OutboxStore;

import java.util.List;

public final class AndroidDurableOutbox implements DurableOutbox {
    private final OutboxStore delegate;

    public AndroidDurableOutbox(OutboxStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<OutboxRecord> claimBatch(int limit, long now, long leaseDurationMs) {
        return delegate.claimBatch(limit, now, leaseDurationMs);
    }

    @Override
    public void acknowledge(long id) {
        delegate.acknowledge(id);
    }

    @Override
    public void release(long id, String error, long retryAt) {
        delegate.release(id, error, retryAt);
    }
}
