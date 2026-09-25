package com.textureflow.intelligence.shield;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Model-facing context from doc §8.1. No keys, packages, phones, or absolute timestamps. */
public final class ShieldedContext {
    private final ShieldedPerson person;
    private final String app;
    private final List<ShieldedEvent> events;
    private final String userInstruction;
    private final TokenBudgetReport budget;
    private final boolean truncated;
    private final boolean injectionFlagged;

    public ShieldedContext(
            ShieldedPerson person,
            String app,
            List<ShieldedEvent> events,
            String userInstruction,
            TokenBudgetReport budget,
            boolean truncated,
            boolean injectionFlagged) {
        this.person = person == null ? new ShieldedPerson("", "") : person;
        this.app = app == null ? "" : app;
        this.events = copyEvents(events);
        this.userInstruction = userInstruction == null ? "" : userInstruction;
        this.budget = budget == null ? new TokenBudgetReport(0, 0, false) : budget;
        this.truncated = truncated;
        this.injectionFlagged = injectionFlagged;
    }

    public ShieldedPerson getPerson() {
        return person;
    }

    public String getApp() {
        return app;
    }

    public List<ShieldedEvent> getEvents() {
        return events;
    }

    public String getUserInstruction() {
        return userInstruction;
    }

    public TokenBudgetReport getBudget() {
        return budget;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public boolean isInjectionFlagged() {
        return injectionFlagged;
    }

    public int inputTokens() {
        return budget.inputTokens();
    }

    public int droppedTokens() {
        return budget.droppedTokens();
    }

    public boolean contextOverflow() {
        return budget.contextOverflow();
    }

    public JSONObject toModelJson() {
        try {
            JSONObject personJson = new JSONObject();
            personJson.put("displayName", person.getDisplayName());
            if (!person.getRelationship().isEmpty()) {
                personJson.put("relationship", person.getRelationship());
            }
            JSONArray eventsJson = new JSONArray();
            for (ShieldedEvent event : events) {
                JSONObject row = new JSONObject();
                row.put("ageMinutes", event.getAgeMinutes());
                row.put("text", event.getText());
                eventsJson.put(row);
            }
            JSONObject root = new JSONObject();
            root.put("person", personJson);
            root.put("app", app);
            root.put("events", eventsJson);
            if (!userInstruction.isEmpty()) {
                root.put("userInstruction", userInstruction);
            }
            return root;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize shielded context", e);
        }
    }

    private static List<ShieldedEvent> copyEvents(List<ShieldedEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}
