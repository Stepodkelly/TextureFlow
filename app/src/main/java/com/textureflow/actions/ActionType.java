package com.textureflow.actions;

public enum ActionType {
    REPLY,
    DISMISS,
    SNOOZE;

    public static ActionType fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Action type is required");
        }
        return ActionType.valueOf(value.trim().toUpperCase());
    }
}
