package com.textureflow.data;

public final class OutboxRecord {
    private final long id;
    private final String operationKey;
    private final String kind;
    private final String aggregateId;
    private final String payload;
    private final int attempts;

    public OutboxRecord(long id, String operationKey, String kind, String aggregateId, String payload, int attempts) {
        this.id = id;
        this.operationKey = operationKey;
        this.kind = kind;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.attempts = attempts;
    }

    public long getId() { return id; }
    public String getOperationKey() { return operationKey; }
    public String getKind() { return kind; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
}
