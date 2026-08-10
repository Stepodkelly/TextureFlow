package com.textureflow.data;

public final class EventWriteResult {
    public enum Change { INSERTED, UPDATED, UNCHANGED, REMOVED }

    private final StoredNotificationEvent event;
    private final Change change;

    public EventWriteResult(StoredNotificationEvent event, Change change) {
        this.event = event;
        this.change = change;
    }

    public StoredNotificationEvent getEvent() { return event; }
    public Change getChange() { return change; }
    public boolean enqueuedSync() { return change != Change.UNCHANGED; }
}
