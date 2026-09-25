package com.textureflow.intelligence.ledger;

/** What started an attention tick (doc §5.1). */
public enum TickTrigger {
    EVENT_POSTED,
    EVENT_UPDATED,
    EVENT_REMOVED,
    USER_QUERY,
    DRAFT_REQUEST,
    PERIODIC_REFRESH
}
