package com.textureflow.bank;

/** Latest replyable notification held for a {@link BankKey}. */
public final class BankEntry {
    private final BankKey key;
    private final String eventId;
    private final String notificationKey;
    private final String senderName;
    private final String conversationLabel;
    private final String body;
    private final long updatedAt;
    private final BankState state;
    private final long snoozeUntilMillis;

    public BankEntry(
            BankKey key,
            String eventId,
            String notificationKey,
            String senderName,
            String conversationLabel,
            String body,
            long updatedAt) {
        this(key, eventId, notificationKey, senderName, conversationLabel, body, updatedAt,
                BankState.EMPTY, 0L);
    }

    public BankEntry(
            BankKey key,
            String eventId,
            String notificationKey,
            String senderName,
            String conversationLabel,
            String body,
            long updatedAt,
            BankState state,
            long snoozeUntilMillis) {
        if (key == null) throw new IllegalArgumentException("key is required");
        if (eventId == null || eventId.isEmpty()) {
            throw new IllegalArgumentException("eventId is required");
        }
        this.key = key;
        this.eventId = eventId;
        this.notificationKey = notificationKey == null ? "" : notificationKey;
        this.senderName = senderName == null ? "" : senderName;
        this.conversationLabel = conversationLabel;
        this.body = body;
        this.updatedAt = updatedAt;
        this.state = state == null ? BankState.EMPTY : state;
        this.snoozeUntilMillis = Math.max(0L, snoozeUntilMillis);
    }

    public BankKey key() { return key; }
    public String eventId() { return eventId; }
    public String notificationKey() { return notificationKey; }
    public String senderName() { return senderName; }
    public String conversationLabel() { return conversationLabel; }
    public String body() { return body; }
    public long updatedAt() { return updatedAt; }
    public BankState state() { return state; }
    public long snoozeUntilMillis() { return snoozeUntilMillis; }

    public BankEntry withState(BankState nextState, long snoozeUntil, long now) {
        return new BankEntry(
                key, eventId, notificationKey, senderName, conversationLabel, body,
                now, nextState, snoozeUntil);
    }
}
