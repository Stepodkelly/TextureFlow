package com.textureflow.policy;

import com.textureflow.actions.ActionErrorCode;

public final class PolicyDecision {
    private static final PolicyDecision ALLOWED = new PolicyDecision(true, null, "Allowed");

    private final boolean allowed;
    private final ActionErrorCode errorCode;
    private final String message;

    private PolicyDecision(boolean allowed, ActionErrorCode errorCode, String message) {
        this.allowed = allowed;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static PolicyDecision allow() { return ALLOWED; }
    public static PolicyDecision deny(ActionErrorCode errorCode, String message) {
        return new PolicyDecision(false, errorCode, message);
    }

    public boolean isAllowed() { return allowed; }
    public ActionErrorCode getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
}
