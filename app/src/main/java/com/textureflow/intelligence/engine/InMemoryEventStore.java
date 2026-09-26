package com.textureflow.intelligence.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JVM fake of {@link EventStore} for engine tests and in-process wiring. */
public final class InMemoryEventStore implements EventStore {
    private final Map<String, StoredEvent> events = new LinkedHashMap<>();

    public synchronized void put(StoredEvent event) {
        Objects.requireNonNull(event, "event");
        events.put(event.getEventId(), event);
    }

    public synchronized void remove(String eventId) {
        events.remove(eventId);
    }

    @Override
    public synchronized List<StoredEvent> getLiveForPerson(String personId) {
        List<StoredEvent> live = new ArrayList<>();
        for (StoredEvent event : events.values()) {
            if (!event.isLive()) {
                continue;
            }
            if (personId == null || personId.isEmpty() || personId.equals(event.getPersonId())) {
                live.add(event);
            }
        }
        return Collections.unmodifiableList(live);
    }

    @Override
    public synchronized StoredEvent getByEventId(String eventId) {
        return events.get(eventId);
    }
}
