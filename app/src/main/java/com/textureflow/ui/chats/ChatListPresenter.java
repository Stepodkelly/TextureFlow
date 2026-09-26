package com.textureflow.ui.chats;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.policy.AttentionQueuePolicy;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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
        return groupPeople(events, liveIds, Collections.emptyMap());
    }

    static List<PersonTimeline> groupPeople(
            List<StoredNotificationEvent> events,
            Set<String> liveIds,
            Map<String, AttentionAssessment> assessments) {
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
        return rankPeople(new ArrayList<>(people.values()), assessments);
    }

    static List<PersonTimeline> rankPeople(
            List<PersonTimeline> people, Map<String, AttentionAssessment> assessments) {
        List<PersonTimeline> ranked = new ArrayList<>(people);
        for (PersonTimeline person : ranked) {
            person.assessment = assessmentFor(person, assessments);
        }
        ranked.sort((left, right) -> {
            int urgency = Integer.compare(levelRank(right.assessment), levelRank(left.assessment));
            if (urgency != 0) return urgency;
            return Long.compare(right.latestAt, left.latestAt);
        });
        return ranked;
    }

    static AttentionAssessment assessmentFor(
            PersonTimeline person, Map<String, AttentionAssessment> assessments) {
        if (person == null || assessments == null || assessments.isEmpty()) return null;
        AttentionAssessment byKey = assessments.get(person.personKey);
        if (byKey != null) return byKey;
        AttentionAssessment byName = assessments.get(person.name);
        if (byName != null) return byName;
        for (AttentionAssessment assessment : assessments.values()) {
            if (assessment == null) continue;
            if (!assessment.getPersonId().isEmpty()
                    && (assessment.getPersonId().equals(person.personKey)
                    || assessment.getPersonId().equals(person.name))) {
                return assessment;
            }
            if (assessment.getLeadEventId().isEmpty()) continue;
            for (StoredNotificationEvent event : person.events) {
                if (assessment.getLeadEventId().equals(event.getEventId())) return assessment;
            }
        }
        return null;
    }

    static AttentionAssessment assessmentForEvent(
            StoredNotificationEvent event, Map<String, AttentionAssessment> assessments) {
        if (event == null || assessments == null || assessments.isEmpty()) return null;
        for (AttentionAssessment assessment : assessments.values()) {
            if (assessment != null && event.getEventId().equals(assessment.getLeadEventId())) {
                return assessment;
            }
        }
        String name = emptyFallback(event.getSenderName(), event.getConversationLabel());
        if (name == null) return null;
        AttentionAssessment byKey = assessments.get(normalizePersonKey(name));
        return byKey != null ? byKey : assessments.get(name.trim());
    }

    /** URGENT=2, IMPORTANT=1, everyone else (including no assessment)=0. */
    public static int levelRank(AttentionAssessment assessment) {
        if (assessment == null) return 0;
        return levelRank(assessment.getLevel());
    }

    public static int levelRank(AttentionLevel level) {
        if (level == AttentionLevel.URGENT) return 2;
        if (level == AttentionLevel.IMPORTANT) return 1;
        return 0;
    }

    public static AttentionLevel effectiveLevel(
            StoredNotificationEvent event, Map<String, AttentionAssessment> assessments) {
        AttentionAssessment assessment = assessmentForEvent(event, assessments);
        if (assessment != null) return assessment.getLevel();
        return parseStoredLevel(event == null ? null : event.getPriorityLevel());
    }

    public static AttentionLevel parseStoredLevel(String priorityLevel) {
        if (priorityLevel == null) return AttentionLevel.NORMAL;
        try {
            return AttentionLevel.valueOf(priorityLevel.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return AttentionLevel.NORMAL;
        }
    }

    public static String levelLabel(AttentionLevel level) {
        if (level == null) return "";
        return switch (level) {
            case URGENT -> "Urgent";
            case IMPORTANT -> "Important";
            case NORMAL -> "Normal";
            case LOW -> "Low";
        };
    }

    public static String reasonLine(AttentionAssessment assessment) {
        if (assessment == null) return "";
        String reason = emptyFallback(assessment.getReason(), "");
        String label = levelLabel(assessment.getLevel());
        if (reason.isEmpty()) return label;
        if (label.isEmpty()) return reason;
        return label + " · " + reason;
    }

    public static boolean isOnDeviceModel(AssessmentSource source) {
        return source == AssessmentSource.ON_DEVICE_MODEL;
    }

    public static boolean isOnDeviceModel(AttentionAssessment assessment) {
        return assessment != null && isOnDeviceModel(assessment.getSource());
    }

    /** Ring catch-light intensity. URGENT is strongest. */
    public static float glowIntensity(AttentionLevel level) {
        if (level == AttentionLevel.URGENT) return 0.85f;
        if (level == AttentionLevel.IMPORTANT) return 0.62f;
        if (level == AttentionLevel.NORMAL) return 0.40f;
        if (level == AttentionLevel.LOW) return 0.24f;
        return 0.45f;
    }

    public static int glowAlpha(AttentionLevel level) {
        if (level == AttentionLevel.URGENT) return 168;
        if (level == AttentionLevel.IMPORTANT) return 124;
        if (level == AttentionLevel.NORMAL) return 92;
        if (level == AttentionLevel.LOW) return 72;
        return 100;
    }

    public static boolean shouldGlow(PersonTimeline person) {
        if (person == null) return false;
        if (person.unreadCount > 0) return true;
        return levelRank(person.assessment) > 0;
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
        return attentionQueue(events, handledKeys, hiddenEventUntil, now, Collections.emptyMap());
    }

    public static List<StoredNotificationEvent> attentionQueue(
            List<StoredNotificationEvent> events,
            Set<String> handledKeys,
            Map<String, Long> hiddenEventUntil,
            long now,
            Map<String, AttentionAssessment> assessments) {
        List<StoredNotificationEvent> queue = new ArrayList<>();
        for (StoredNotificationEvent event : events) {
            AttentionLevel level = effectiveLevel(event, assessments);
            if (!AttentionQueuePolicy.shouldSurface(level.name(), event.hasCapability("REPLY"))) {
                continue;
            }
            Long hiddenUntil = hiddenEventUntil.get(event.getEventId());
            if (hiddenUntil != null && hiddenUntil > now) continue;
            if (hiddenUntil != null) hiddenEventUntil.remove(event.getEventId());
            if (!handledKeys.contains(attentionKey(event))) queue.add(event);
        }
        queue.sort((left, right) -> {
            AttentionLevel leftLevel = effectiveLevel(left, assessments);
            AttentionLevel rightLevel = effectiveLevel(right, assessments);
            int urgency = Integer.compare(levelRank(rightLevel), levelRank(leftLevel));
            if (urgency != 0) return urgency;
            int replyability = Boolean.compare(
                    right.hasCapability("REPLY"), left.hasCapability("REPLY"));
            if (replyability != 0) return replyability;
            int priority = Double.compare(right.getPriorityScore(), left.getPriorityScore());
            return priority != 0 ? priority : Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
        });
        return queue;
    }

    /**
     * Deterministic spoken summary for "What needs me?". Uses assessment.level
     * when present, otherwise the stored notification priority. Does not wait
     * for a model result.
     */
    public static String needsMeSpoken(
            List<StoredNotificationEvent> queue, Map<String, AttentionAssessment> assessments) {
        if (queue == null || queue.isEmpty()) return "You are all caught up.";
        int limit = Math.min(3, queue.size());
        StringBuilder spoken = new StringBuilder();
        if (queue.size() == 1) {
            spoken.append(needsMeItem(queue.get(0), assessments, true));
            return spoken.toString();
        }
        spoken.append(queue.size() == 2 ? "Two things need you. " : "A few things need you. ");
        for (int index = 0; index < limit; index++) {
            spoken.append(needsMeItem(queue.get(index), assessments, index == 0));
            if (index < limit - 1) spoken.append(" ");
        }
        return spoken.toString();
    }

    private static String needsMeItem(
            StoredNotificationEvent event,
            Map<String, AttentionAssessment> assessments,
            boolean lead) {
        AttentionLevel level = effectiveLevel(event, assessments);
        String who = emptyFallback(event.getSenderName(), "someone");
        String app = emptyFallback(event.getAppLabel(), "your phone");
        String body = emptyFallback(event.getBody(), "Open TextureFlow for details.");
        if (level == AttentionLevel.URGENT) {
            return (lead ? "Urgent from " : "Also urgent from ")
                    + who + " on " + app + ". " + body;
        }
        if (level == AttentionLevel.IMPORTANT) {
            return (lead ? "Important from " : "Also from ")
                    + who + " on " + app + ". " + body;
        }
        return (lead ? "From " : "Also from ") + who + " on " + app + ". " + body;
    }

    public static boolean looksLikeNeedsMe(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        return normalized.equals("what needs me")
                || normalized.equals("what's urgent")
                || normalized.equals("what is urgent")
                || normalized.equals("what needs my attention")
                || normalized.contains("what needs me");
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
