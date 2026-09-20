package com.textureflow.bank;

/** Lifecycle of a paired outbound bank slot for one peer × messenger package. */
public enum BankState {
    EMPTY,
    ARMED_SNOOZED,
    WAKING,
    LIVE,
    DRAINING_OUTBOX,
    SPENT,
    REFILL_PENDING;

    static BankState fromStorage(String raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        try {
            return BankState.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return EMPTY;
        }
    }
}
