package com.textureflow.intelligence.engine;

import java.util.List;

/**
 * Repository port for attention ticks. Implementations must not expose Android types
 * so engine tests can run on the JVM.
 */
public interface EventStore {
    /** Live events ({@code status != REMOVED}) for one person. */
    List<StoredEvent> getLiveForPerson(String personId);

    /** Any stored row for this event, including {@code REMOVED}. {@code null} if unknown. */
    StoredEvent getByEventId(String eventId);
}
