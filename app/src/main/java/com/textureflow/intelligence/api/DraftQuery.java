package com.textureflow.intelligence.api;

import java.util.Objects;

/** User-initiated draft request for one event. */
public final class DraftQuery {
    private final String eventId;
    private final String userRequest;
    private final String userDraftText;
    private final ReplyTone preferredTone;

    public DraftQuery(
            String eventId,
            String userRequest,
            String userDraftText,
            ReplyTone preferredTone) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.userRequest = userRequest == null ? "" : userRequest;
        this.userDraftText = userDraftText == null ? "" : userDraftText;
        this.preferredTone = preferredTone;
    }

    public String getEventId() { return eventId; }
    public String getUserRequest() { return userRequest; }
    public String getUserDraftText() { return userDraftText; }
    public ReplyTone getPreferredTone() { return preferredTone; }
}
