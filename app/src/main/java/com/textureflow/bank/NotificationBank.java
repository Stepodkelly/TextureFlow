package com.textureflow.bank;

import com.textureflow.actions.NotificationControl;

import java.util.List;

/**
 * Messenger-agnostic paired outbound bank: latest replyable notification per
 * peer × package, arming/wake, local outbox, and stub handshake refill.
 */
public final class NotificationBank {
    private final BankStore store;
    private final OutboundMessageOutbox outbox;
    private final HandshakeRefill refill;

    public NotificationBank(
            BankStore store, OutboundMessageOutbox outbox, HandshakeRefill refill) {
        if (store == null) throw new IllegalArgumentException("store is required");
        if (outbox == null) throw new IllegalArgumentException("outbox is required");
        if (refill == null) throw new IllegalArgumentException("refill is required");
        this.store = store;
        this.outbox = outbox;
        this.refill = refill;
    }

    /** Upsert the latest replyable entry; marks ARMED_SNOOZED without touching shade. */
    public void adopt(BankEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        long now = entry.updatedAt() > 0L ? entry.updatedAt() : System.currentTimeMillis();
        store.upsert(entry.withState(BankState.ARMED_SNOOZED, entry.snoozeUntilMillis(), now));
        refill.clear(entry.key());
    }

    /**
     * Adopt then rolling-snooze. Control/snooze failures never throw — entry stays banked.
     *
     * @return true when snooze arming succeeded
     */
    public boolean adoptAndArm(NotificationControl control, BankEntry entry) {
        adopt(entry);
        BankEntry stored = store.peek(entry.key());
        if (stored == null) return false;
        boolean armed = BankArming.refreshArm(control, stored);
        long now = System.currentTimeMillis();
        long until = armed ? now + BankArming.DEFAULT_ARM_SNOOZE_MS : stored.snoozeUntilMillis();
        store.upsert(stored.withState(BankState.ARMED_SNOOZED, until, now));
        return armed;
    }

    public BankEntry peek(BankKey key) {
        return store.peek(key);
    }

    public void clear(BankKey key) {
        store.clear(key);
        if (key != null) refill.clear(key);
    }

    /**
     * Wake a banked notification (~100 ms re-snooze) for cold-start send.
     *
     * @return true when wake snooze was accepted
     */
    public boolean requestWake(NotificationControl control, BankKey key) {
        BankEntry entry = store.peek(key);
        if (entry == null) return false;
        boolean woke = BankArming.wake(control, entry);
        if (!woke) return false;
        long now = System.currentTimeMillis();
        store.upsert(entry.withState(BankState.WAKING, now + BankArming.WAKE_MS, now));
        return true;
    }

    /**
     * After a successful wake, the listener must adopt the reposted handle without
     * immediately re-arming snooze — otherwise cold-start never gets a LIVE window.
     */
    public BankEntry adoptAfterWake(BankEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        long now = entry.updatedAt() > 0L ? entry.updatedAt() : System.currentTimeMillis();
        BankState next = outbox.hasPendingFor(entry.key())
                ? BankState.DRAINING_OUTBOX
                : BankState.LIVE;
        BankEntry live = entry.withState(next, 0L, now);
        store.upsert(live);
        refill.clear(entry.key());
        return live;
    }

    public String enqueueOutbound(String peerKey, String packageName, String body) {
        return outbox.enqueue(peerKey, packageName, body);
    }

    public List<OutboundMessage> peekOutbound(int limit) {
        return outbox.peekReady(limit);
    }

    public void markOutboundSent(String id) {
        outbox.markSent(id);
    }

    public void markOutboundFailed(String id, String error) {
        outbox.markFailed(id, error);
    }

    /** Transition toward LIVE / DRAINING_OUTBOX when a live REPLY handle matches a banked event. */
    public BankEntry onLiveHandle(String eventId) {
        BankEntry entry = store.peekByEventId(eventId);
        if (entry == null) return null;
        long now = System.currentTimeMillis();
        BankState next = outbox.hasPendingFor(entry.key())
                ? BankState.DRAINING_OUTBOX
                : BankState.LIVE;
        BankEntry updated = entry.withState(next, 0L, now);
        store.upsert(updated);
        return updated;
    }

    public void markSpent(BankKey key) {
        BankEntry entry = store.peek(key);
        if (entry == null) return;
        long now = System.currentTimeMillis();
        store.upsert(entry.withState(BankState.SPENT, 0L, now));
    }

    /** Stub refill request when the bank slot is empty or spent. */
    public void requestRefill(BankKey key) {
        if (key == null) throw new IllegalArgumentException("key is required");
        refill.request(key);
        BankEntry entry = store.peek(key);
        long now = System.currentTimeMillis();
        if (entry == null) {
            // Persist a placeholder-free pending marker only in refill prefs; no empty BankEntry.
            return;
        }
        store.upsert(entry.withState(BankState.REFILL_PENDING, 0L, now));
    }

    public boolean isRefillPending(BankKey key) {
        return refill.isPending(key);
    }

    public OutboundMessageOutbox outbox() { return outbox; }
    public HandshakeRefill refill() { return refill; }
}
