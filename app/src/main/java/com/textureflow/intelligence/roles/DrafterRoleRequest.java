package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.api.ReplyTone;
import com.textureflow.intelligence.shield.ShieldedContext;

import java.util.Objects;

/** One A4 draft call. User words are the authority (doc §6.3). */
public final class DrafterRoleRequest {
    private final ShieldedContext context;
    private final String userRequest;
    private final String userDraftText;
    private final ReplyTone preferredTone;
    private final String eventRecipient;
    private final String eventPackageName;
    private final String eventStatus;
    private final Integer tickEventVersion;
    private final Integer currentEventVersion;
    private final Double systemConfidence;
    private final boolean injectionFlagged;

    public DrafterRoleRequest(
            ShieldedContext context,
            String userRequest,
            String userDraftText,
            ReplyTone preferredTone) {
        this(
                context,
                userRequest,
                userDraftText,
                preferredTone,
                "",
                "",
                "ACTIVE",
                1,
                1,
                null,
                context != null && context.isInjectionFlagged());
    }

    public DrafterRoleRequest(
            ShieldedContext context,
            String userRequest,
            String userDraftText,
            ReplyTone preferredTone,
            String eventRecipient,
            String eventPackageName,
            String eventStatus,
            Integer tickEventVersion,
            Integer currentEventVersion,
            Double systemConfidence,
            boolean injectionFlagged) {
        this.context = Objects.requireNonNull(context, "context");
        this.userRequest = userRequest == null ? "" : userRequest;
        this.userDraftText = userDraftText == null ? "" : userDraftText;
        this.preferredTone = preferredTone;
        this.eventRecipient = eventRecipient == null ? "" : eventRecipient;
        this.eventPackageName = eventPackageName == null ? "" : eventPackageName;
        this.eventStatus = eventStatus;
        this.tickEventVersion = tickEventVersion;
        this.currentEventVersion = currentEventVersion;
        this.systemConfidence = systemConfidence;
        this.injectionFlagged = injectionFlagged || context.isInjectionFlagged();
    }

    public ShieldedContext getContext() {
        return context;
    }

    public String getUserRequest() {
        return userRequest;
    }

    public String getUserDraftText() {
        return userDraftText;
    }

    public ReplyTone getPreferredTone() {
        return preferredTone;
    }

    public String getEventRecipient() {
        return eventRecipient;
    }

    public String getEventPackageName() {
        return eventPackageName;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public Integer getTickEventVersion() {
        return tickEventVersion;
    }

    public Integer getCurrentEventVersion() {
        return currentEventVersion;
    }

    public Double getSystemConfidence() {
        return systemConfidence;
    }

    public boolean injectionFlagged() {
        return injectionFlagged;
    }
}
