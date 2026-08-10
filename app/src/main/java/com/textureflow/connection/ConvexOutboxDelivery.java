package com.textureflow.connection;

import com.textureflow.data.OutboxRecord;

import org.json.JSONObject;

public final class ConvexOutboxDelivery implements OutboxDelivery {
    private final RemoteGateway gateway;
    private final ClaimStore claims;

    public ConvexOutboxDelivery(RemoteGateway gateway, ClaimStore claims) {
        this.gateway = gateway;
        this.claims = claims;
    }

    @Override
    public void deliver(OutboxRecord record) throws Exception {
        if ("EVENT_UPSERT".equals(record.getKind())) {
            String traceId = ConnectionIds.stableTraceId(record.getOperationKey());
            JSONObject event = new JSONObject(record.getPayload());
            if ("REMOVED".equals(event.optString("status"))) {
                gateway.uploadRemovedEvent(record.getPayload(), traceId);
            } else {
                gateway.uploadEvent(record.getPayload(), traceId);
            }
            return;
        }
        if ("RECEIPT_COMPLETE".equals(record.getKind())) {
            ClaimRecord claim = claims.find(record.getAggregateId());
            if (claim == null) {
                throw new IllegalStateException("Receipt has no persisted command claim; refusing unsafe upload");
            }
            gateway.uploadReceipt(record.getPayload(), claim);
            return;
        }
        throw new IllegalArgumentException("Unsupported durable outbox kind: " + record.getKind());
    }

    @Override
    public void afterAcknowledged(OutboxRecord record) {
        if ("RECEIPT_COMPLETE".equals(record.getKind())) {
            claims.remove(record.getAggregateId());
        }
    }
}
