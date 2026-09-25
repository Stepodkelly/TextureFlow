package com.textureflow.ui.chats;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.policy.AttentionQueuePolicy;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure chat-list and attention-queue logic. No Android imports. */
public final class ChatListPresenter {
    private ChatListPresenter() {}

    public static boolean isConversationCandidate(StoredNotificationEvent event) {
        if (event.hasCapability("REPLY")) return true;
        String hay = ((event.getPackageName() == null ? "" : event.getPackageName()) + " "
                + (event.getAppLabel() == null ? "" : event.getAppLabel())).toLowerCase(Locale.US);
        return hay.contains("whatsapp")
                || hay.contains("telegram")
                || hay.contains("signal")
                || hay.contains("instagram")
                || hay.contains("messenger")
                || hay.contains("facebook.orca")
                || hay.contains("sms")
                || hay.contains("mms")
                || hay.contains("messaging")
                || hay.contains("imessage")
                || hay.contains("discord")
                || hay.contains("slack")
                || hay.contains("viber")
                || hay.contains("line");
    }

    static List<PersonTimeline> groupPeople(
            List<StoredNotificationEvent> events, Set<String> liveIds) {
        Map<String, PersonTimeline> people = new LinkedHashMap<>();
        for (StoredNotificationEvent event : events) {
            if (!isConversationCandidate(event)) continue;
            String name = emptyFallback(event.getSenderName(), event.getConversationLabel());
            if (name == null || name.trim().isEmpty()) continue;
            if ("unknown sender".equalsIgnoreCase(name.trim())) continue;
            String key = normalizePersonKey(name);
            PersonTimeline person = people.get(key);
            if (person == null) {
                person = new PersonTimeline(name.trim());
                people.put(key, person);
            }
            person.events.add(event);
            person.apps.add(emptyFallback(event.getAppLabel(), event.getPackageName()));
            if (event.getUpdatedAt() >= person.latestAt) {
                person.latestAt = event.getUpdatedAt();
                person.latestPackage = event.getPackageName();
                person.latestAppLabel = event.getAppLabel();
                person.latestBody = emptyFallback(event.getBody(), "Notification");
            }
            if (liveIds.contains(event.getEventId())) {
                person.unreadCount++;
                if (event.getUpdatedAt() >= person.latestUnreadAt) {
                    person.latestUnreadAt = event.getUpdatedAt();
                    person.latestUnreadPackage = event.getPackageName();
                    person.latestUnreadAppLabel = event.getAppLabel();
                }
            }
        }
        List<PersonTimeline> ordered = new ArrayList<>(people.values());
        ordered.sort((left, right) -> Long.compare(right.latestAt, left.latestAt));
        return ordered;
    }

    static PersonTimeline demoAlex(long now) {
        PersonTimeline alex = new PersonTimeline("Alex");
        alex.latestAt = now;
        alex.latestBody = "😊 See you in 5 minutes";
        alex.latestPackage = "com.whatsapp";
        alex.latestAppLabel = "WhatsApp";
        alex.latestUnreadPackage = "com.whatsapp";
        alex.latestUnreadAppLabel = "WhatsApp";
        alex.latestUnreadAt = alex.latestAt;
        alex.unreadCount = 1;
        alex.apps.add("WhatsApp");
        alex.demo = true;
        alex.demoMessages.add(new ChatBubble(false, "Hey — still good for later?", alex.latestAt - 12 * 60_000L));
        alex.demoMessages.add(new ChatBubble(true, "Yes, heading over now.", alex.latestAt - 8 * 60_000L));
        alex.demoMessages.add(new ChatBubble(false, "😊 See you in 5 minutes", alex.latestAt));
        return alex;
    }

    static boolean matchesQuery(PersonTimeline person, String needle) {
        if (needle == null || needle.isEmpty()) return true;
        return person.name.toLowerCase(Locale.US).contains(needle)
                || person.latestBody.toLowerCase(Locale.US).contains(needle)
                || String.join(" ", person.apps).toLowerCase(Locale.US).contains(needle);
    }

    static List<PersonTimeline> filterPeople(List<PersonTimeline> people, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<PersonTimeline> visible = new ArrayList<>();
        for (PersonTimeline person : people) {
            if (matchesQuery(person, needle)) visible.add(person);
        }
        return visible;
    }

    public static List<StoredNotificationEvent> attentionQueue(
            List<StoredNotificationEvent> events,
            Set<String> handledKeys,
            Map<String, Long> hiddenEventUntil,
            long now) {
        List<StoredNotificationEvent> queue = new ArrayList<>();
        for (StoredNotificationEvent event : events) {
            if (!AttentionQueuePolicy.shouldSurface(
                    event.getPriorityLevel(), event.hasCapability("REPLY"))) continue;
            Long hiddenUntil = hiddenEventUntil.get(event.getEventId());
            if (hiddenUntil != null && hiddenUntil > now) continue;
            if (hiddenUntil != null) hiddenEventUntil.remove(event.getEventId());
            if (!handledKeys.contains(attentionKey(event))) queue.add(event);
        }
        queue.sort((left, right) -> {
            int urgency = Boolean.compare(
                    "URGENT".equals(right.getPriorityLevel()),
                    "URGENT".equals(left.getPriorityLevel()));
            if (urgency != 0) return urgency;
            int replyability = Boolean.compare(
                    right.hasCapability("REPLY"), left.hasCapability("REPLY"));
            if (replyability != 0) return replyability;
            int priority = Double.compare(right.getPriorityScore(), left.getPriorityScore());
            return priority != 0 ? priority : Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
        });
        return queue;
    }

    public static String suggestReply(StoredNotificationEvent event) {
        String body = emptyFallback(event.getBody(), "").toLowerCase(Locale.US);
        if (body.contains("where") || body.contains("when")) {
            return "Thanks for asking. Let me confirm and get back to you shortly.";
        }
        if (body.contains("can you") || body.contains("could you") || body.contains("?")) {
            return "Yes, that works for me. Thanks for checking.";
        }
        return "Thanks for letting me know.";
    }

    public static boolean looksLikeHistoryQuestion(String value) {
        return value.contains("what did") || value.contains("previous conversation")
                || value.contains("yesterday") || value.contains("last message")
                || value.contains("conversation with");
    }

    public static StoredNotificationEvent matchHistory(
            String question, List<StoredNotificationEvent> recent) {
        String normalized = question.toLowerCase(Locale.US);
        List<StoredNotificationEvent> matches = new ArrayList<>();
        for (StoredNotificationEvent event : recent) {
            String sender = emptyFallback(event.getSenderName(), "").toLowerCase(Locale.US);
            if (!sender.isEmpty() && normalized.contains(sender)) matches.add(event);
        }
        if (matches.isEmpty()) return null;
        matches.sort((left, right) -> Long.compare(right.getUpdatedAt(), left.getUpdatedAt()));
        return matches.get(0);
    }

    public static String normalizePersonKey(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "").trim();
    }

    public static String eventSignature(List<StoredNotificationEvent> events) {
        StringBuilder value = new StringBuilder();
        for (StoredNotificationEvent event : events) {
            value.append(event.getEventId()).append(':').append(event.getVersion())
                    .append(':').append(event.getStatus()).append('|');
        }
        return value.toString();
    }

    public static String attentionKey(StoredNotificationEvent event) {
        return event.getEventId() + ':' + event.getVersion();
    }

    public static String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static String joinStatus(String primary, String detail) {
        if (detail == null || detail.trim().isEmpty()) return primary;
        return primary + " · " + detail.trim();
    }

    public static String safeId(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static String shortTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(millis));
    }

    public static String clockTime(long millis) {
        // Always show a complete wall-clock time, e.g. "4:55 PM".
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(millis));
    }
}
