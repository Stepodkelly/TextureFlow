package com.textureflow.bank;

/** One queued outbound text waiting for a live REPLY handle. */
public final class OutboundMessage {
    private final String id;
    private final String peerKey;
    private final String packageName;
    private final String body;
    private final long createdAt;
    private final int attempts;
    private final String lastError;

    public OutboundMessage(
            String id,
            String peerKey,
            String packageName,
            String body,
            long createdAt,
            int attempts,
            String lastError) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
        if (peerKey == null || peerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("peerKey is required");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName is required");
        }
        if (body == null) throw new IllegalArgumentException("body is required");
        this.id = id;
        this.peerKey = peerKey.trim();
        this.packageName = packageName.trim();
        this.body = body;
        this.createdAt = createdAt;
        this.attempts = Math.max(0, attempts);
        this.lastError = lastError;
    }

    public String id() { return id; }
    public String peerKey() { return peerKey; }
    public String packageName() { return packageName; }
    public String body() { return body; }
    public long createdAt() { return createdAt; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }

    public BankKey bankKey() {
        return new BankKey(peerKey, packageName);
    }

    OutboundMessage withFailure(String error) {
        return new OutboundMessage(
                id, peerKey, packageName, body, createdAt, attempts + 1, error);
    }
}
