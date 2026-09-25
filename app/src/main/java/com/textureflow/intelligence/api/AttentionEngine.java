package com.textureflow.intelligence.api;

/**
 * In-process intelligence contract (doc §7).
 * Implementations must not add execute, send, or dispatch methods,
 * and must never accept live notification handles.
 */
public interface AttentionEngine {
    void onEvent(EventSignal signal);

    void requestSummary(SummaryQuery query, Callback<SummaryResult> cb);

    void requestDraft(DraftQuery query, Callback<ProposalDraft> cb);

    void addListener(AttentionListener listener);

    CapabilityProfile capability();
}
