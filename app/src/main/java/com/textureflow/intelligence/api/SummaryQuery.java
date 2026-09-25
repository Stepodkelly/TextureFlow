package com.textureflow.intelligence.api;

import java.util.Objects;

/** User- or voice-initiated summary request for one person. */
public final class SummaryQuery {
    private final String personId;
    private final String userRequest;
    private final boolean forceModel;

    public SummaryQuery(String personId, String userRequest, boolean forceModel) {
        this.personId = Objects.requireNonNull(personId, "personId");
        this.userRequest = userRequest == null ? "" : userRequest;
        this.forceModel = forceModel;
    }

    public String getPersonId() { return personId; }
    public String getUserRequest() { return userRequest; }
    public boolean isForceModel() { return forceModel; }
}
