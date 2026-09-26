package com.textureflow.ui.chats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.AttentionAssessment;
import com.textureflow.intelligence.api.AttentionLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class ChatListPresenterTest {
    @Test
    public void replyCapableEventIsConversationCandidate() {
        assertTrue(ChatListPresenter.isConversationCandidate(
                event("e1", "Sam", "com.mail.app", "Mail", "Hi", 1L, "NORMAL", 0.2, Set.of("REPLY"))));
    }

    @Test
    public void messagingPackageIsConversationCandidateWithoutReply() {
        assertTrue(ChatListPresenter.isConversationCandidate(
                event("e1", "Sam", "com.whatsapp", "WhatsApp", "Hi", 1L, "NORMAL", 0.2, Set.of())));
    }

    @Test
    public void unrelatedPackageIsNotConversationCandidate() {
        assertFalse(ChatListPresenter.isConversationCandidate(
                event("e1", "Bank", "com.bank.app", "Bank", "OTP", 1L, "NORMAL", 0.2, Set.of())));
    }

    @Test
    public void ranksUrgentThenImportantThenRecency() {
        List<StoredNotificationEvent> events = List.of(
                event("old-urgent", "Pat", "com.whatsapp", "WhatsApp", "now", 5L, "NORMAL", 0.2, Set.of("REPLY")),
                event("fresh-normal", "Sam", "com.whatsapp", "WhatsApp", "later", 40L, "NORMAL", 0.2, Set.of("REPLY")),
                event("mid-important", "Lee", "com.whatsapp", "WhatsApp", "due", 20L, "NORMAL", 0.2, Set.of("REPLY")));
        Map<String, AttentionAssessment> assessments = Map.of(
                "pat", assessment("pat", "old-urgent", AttentionLevel.URGENT, "time pressure",
                        AssessmentSource.DETERMINISTIC),
                "lee", assessment("lee", "mid-important", AttentionLevel.IMPORTANT, "needs a reply",
                        AssessmentSource.DETERMINISTIC));
        List<PersonTimeline> people = ChatListPresenter.groupPeople(events, Set.of(), assessments);
        assertEquals(List.of("Pat", "Lee", "Sam"), people.stream().map(person -> person.name).toList());
        assertEquals("time pressure", people.get(0).assessment.getReason());
        assertEquals(AttentionLevel.IMPORTANT, people.get(1).assessment.getLevel());
        assertNull(people.get(2).assessment);
    }

    @Test
    public void sameLevelKeepsRecency() {
        List<StoredNotificationEvent> events = List.of(
                event("older", "Pat", "com.whatsapp", "WhatsApp", "earlier", 10L, "NORMAL", 0.2, Set.of("REPLY")),
                event("newer", "Sam", "com.whatsapp", "WhatsApp", "later", 30L, "NORMAL", 0.2, Set.of("REPLY")));
        Map<String, AttentionAssessment> assessments = Map.of(
                "pat", assessment("pat", "older", AttentionLevel.IMPORTANT, "a", AssessmentSource.DETERMINISTIC),
                "sam", assessment("sam", "newer", AttentionLevel.IMPORTANT, "b", AssessmentSource.DETERMINISTIC));
        List<PersonTimeline> people = ChatListPresenter.groupPeople(events, Set.of(), assessments);
        assertEquals("Sam", people.get(0).name);
        assertEquals("Pat", people.get(1).name);
    }

    @Test
    public void matchesAssessmentByLeadEventIdWhenPersonIdDiffers() {
        List<StoredNotificationEvent> events = List.of(
                event("e-sam", "Sam", "com.whatsapp", "WhatsApp", "hi", 10L, "NORMAL", 0.2, Set.of("REPLY")));
        Map<String, AttentionAssessment> assessments = Map.of(
                "person_sam", assessment("person_sam", "e-sam", AttentionLevel.URGENT, "direct request",
                        AssessmentSource.ON_DEVICE_MODEL));
        List<PersonTimeline> people = ChatListPresenter.groupPeople(events, Set.of(), assessments);
        assertEquals(AttentionLevel.URGENT, people.get(0).assessment.getLevel());
        assertTrue(ChatListPresenter.isOnDeviceModel(people.get(0).assessment));
        assertEquals("Urgent · direct request", ChatListPresenter.reasonLine(people.get(0).assessment));
    }

    @Test
    public void glowIntensityIsStrongestForUrgent() {
        assertTrue(ChatListPresenter.glowIntensity(AttentionLevel.URGENT)
                > ChatListPresenter.glowIntensity(AttentionLevel.IMPORTANT));
        assertTrue(ChatListPresenter.glowIntensity(AttentionLevel.IMPORTANT)
                > ChatListPresenter.glowIntensity(AttentionLevel.NORMAL));
        assertTrue(ChatListPresenter.glowIntensity(AttentionLevel.NORMAL)
                > ChatListPresenter.glowIntensity(AttentionLevel.LOW));
    }

    @Test
    public void groupsPeopleByNormalizedNameAndKeepsLatestSnippet() {
        List<StoredNotificationEvent> events = List.of(
                event("e1", "Alex Chen", "com.whatsapp", "WhatsApp", "first", 10L, "NORMAL", 0.2, Set.of("REPLY")),
                event("e2", "alex  chen", "org.telegram.messenger", "Telegram", "later", 20L, "NORMAL", 0.3, Set.of("REPLY")),
                event("e3", "Sam", "com.whatsapp", "WhatsApp", "other", 15L, "NORMAL", 0.2, Set.of("REPLY")));
        List<PersonTimeline> people = ChatListPresenter.groupPeople(events, Set.of("e2"));
        assertEquals(2, people.size());
        assertEquals("Alex Chen", people.get(0).name);
        assertEquals("later", people.get(0).latestBody);
        assertEquals(1, people.get(0).unreadCount);
        assertEquals("Sam", people.get(1).name);
        assertEquals(0, people.get(1).unreadCount);
    }

    @Test
    public void skipsUnknownSenderAndEmptyNames() {
        List<StoredNotificationEvent> events = List.of(
                event("e1", "Unknown sender", "com.whatsapp", "WhatsApp", "x", 1L, "NORMAL", 0.2, Set.of("REPLY")),
                event("e2", "  ", "com.whatsapp", "WhatsApp", "y", 2L, "NORMAL", 0.2, Set.of("REPLY")));
        assertTrue(ChatListPresenter.groupPeople(events, Set.of()).isEmpty());
    }

    @Test
    public void filterMatchesNameBodyOrApp() {
        PersonTimeline alex = ChatListPresenter.demoAlex(1_000L);
        PersonTimeline sam = new PersonTimeline("Sam");
        sam.latestBody = "running late";
        sam.apps.add("Signal");
        List<PersonTimeline> people = List.of(alex, sam);

        assertEquals(1, ChatListPresenter.filterPeople(people, "alex").size());
        assertEquals(1, ChatListPresenter.filterPeople(people, "See you").size());
        assertEquals(1, ChatListPresenter.filterPeople(people, "signal").size());
        assertTrue(ChatListPresenter.filterPeople(people, "nomatch").isEmpty());
        assertEquals(2, ChatListPresenter.filterPeople(people, "").size());
    }

    @Test
    public void attentionQueueRanksUrgentThenImportantThenReply() {
        StoredNotificationEvent ordinaryReply = event(
                "a", "Sam", "com.whatsapp", "WhatsApp", "hi", 10L, "NORMAL", 0.4, Set.of("REPLY"));
        StoredNotificationEvent urgent = event(
                "b", "Pat", "com.whatsapp", "WhatsApp", "now", 5L, "URGENT", 0.1, Set.of("REPLY"));
        StoredNotificationEvent importantNoReply = event(
                "c", "Lee", "com.bank", "Bank", "due", 20L, "IMPORTANT", 0.9, Set.of());
        List<StoredNotificationEvent> queue = ChatListPresenter.attentionQueue(
                List.of(ordinaryReply, urgent, importantNoReply),
                Set.of(),
                new LinkedHashMap<>(),
                100L);
        assertEquals("b", queue.get(0).getEventId());
        assertEquals("c", queue.get(1).getEventId());
        assertEquals("a", queue.get(2).getEventId());
    }

    @Test
    public void attentionQueueSkipsHandledAndStillHiddenEvents() {
        StoredNotificationEvent live = event(
                "a", "Sam", "com.whatsapp", "WhatsApp", "hi", 10L, "URGENT", 0.8, Set.of("REPLY"));
        Set<String> handled = new LinkedHashSet<>();
        handled.add(ChatListPresenter.attentionKey(live));
        Map<String, Long> hidden = new LinkedHashMap<>();
        hidden.put("a", 50L);
        assertTrue(ChatListPresenter.attentionQueue(List.of(live), handled, hidden, 10L).isEmpty());

        handled.clear();
        hidden.put("a", 5L);
        List<StoredNotificationEvent> queue =
                ChatListPresenter.attentionQueue(List.of(live), handled, hidden, 10L);
        assertEquals(1, queue.size());
        assertFalse(hidden.containsKey("a"));
    }

    @Test
    public void suggestReplyUsesQuestionAndThanksFallbacks() {
        assertEquals("Thanks for asking. Let me confirm and get back to you shortly.",
                ChatListPresenter.suggestReply(event(
                        "e", "Sam", "com.whatsapp", "WhatsApp", "When are you free?",
                        1L, "NORMAL", 0.2, Set.of("REPLY"))));
        assertEquals("Yes, that works for me. Thanks for checking.",
                ChatListPresenter.suggestReply(event(
                        "e", "Sam", "com.whatsapp", "WhatsApp", "Can you make it?",
                        1L, "NORMAL", 0.2, Set.of("REPLY"))));
        assertEquals("Thanks for letting me know.",
                ChatListPresenter.suggestReply(event(
                        "e", "Sam", "com.whatsapp", "WhatsApp", "Landed safely.",
                        1L, "NORMAL", 0.2, Set.of("REPLY"))));
    }

    @Test
    public void historyMatchPicksNewestSenderHit() {
        assertTrue(ChatListPresenter.looksLikeHistoryQuestion("what did sam say"));
        assertFalse(ChatListPresenter.looksLikeHistoryQuestion("send that"));
        List<StoredNotificationEvent> recent = List.of(
                event("old", "Sam", "com.whatsapp", "WhatsApp", "earlier", 10L, "NORMAL", 0.2, Set.of("REPLY")),
                event("new", "Sam", "com.whatsapp", "WhatsApp", "latest", 20L, "NORMAL", 0.2, Set.of("REPLY")));
        StoredNotificationEvent match = ChatListPresenter.matchHistory("What did Sam say yesterday?", recent);
        assertEquals("new", match.getEventId());
        assertNull(ChatListPresenter.matchHistory("What did Riley say?", recent));
    }

    @Test
    public void normalizePersonKeyStripsPunctuation() {
        assertEquals("alexchen", ChatListPresenter.normalizePersonKey("Alex Chen"));
        assertEquals("alexchen", ChatListPresenter.normalizePersonKey("alex-chen!"));
    }

    @Test
    public void attentionQueuePrefersAssessmentLevelOverStoredPriority() {
        StoredNotificationEvent storedNormal = event(
                "a", "Sam", "com.whatsapp", "WhatsApp", "hi", 10L, "NORMAL", 0.4, Set.of("REPLY"));
        StoredNotificationEvent storedUrgent = event(
                "b", "Pat", "com.whatsapp", "WhatsApp", "later", 20L, "URGENT", 0.9, Set.of("REPLY"));
        Map<String, AttentionAssessment> assessments = Map.of(
                "a", assessment("sam", "a", AttentionLevel.URGENT, "needs you",
                        AssessmentSource.DETERMINISTIC),
                "b", assessment("pat", "b", AttentionLevel.IMPORTANT, "follow up",
                        AssessmentSource.DETERMINISTIC));
        List<StoredNotificationEvent> queue = ChatListPresenter.attentionQueue(
                List.of(storedNormal, storedUrgent),
                Set.of(),
                new LinkedHashMap<>(),
                100L,
                assessments);
        assertEquals("a", queue.get(0).getEventId());
        assertEquals(AttentionLevel.URGENT, ChatListPresenter.effectiveLevel(queue.get(0), assessments));
    }

    @Test
    public void needsMeSpokenUsesDeterministicTopItems() {
        assertEquals("You are all caught up.",
                ChatListPresenter.needsMeSpoken(List.of(), Map.of()));
        StoredNotificationEvent urgent = event(
                "a", "Sam", "com.whatsapp", "WhatsApp", "Come down", 10L, "NORMAL", 0.2, Set.of("REPLY"));
        StoredNotificationEvent important = event(
                "b", "Maya", "com.whatsapp", "WhatsApp", "Dinner still on?", 8L, "NORMAL", 0.2, Set.of("REPLY"));
        Map<String, AttentionAssessment> assessments = Map.of(
                "a", assessment("sam", "a", AttentionLevel.URGENT, "direct request",
                        AssessmentSource.DETERMINISTIC),
                "b", assessment("maya", "b", AttentionLevel.IMPORTANT, "question",
                        AssessmentSource.DETERMINISTIC));
        String spoken = ChatListPresenter.needsMeSpoken(List.of(urgent, important), assessments);
        assertTrue(spoken.startsWith("Two things need you."));
        assertTrue(spoken.contains("Urgent from Sam on WhatsApp. Come down"));
        assertTrue(spoken.contains("Also from Maya on WhatsApp. Dinner still on?"));
        assertTrue(ChatListPresenter.looksLikeNeedsMe("what needs me"));
        assertTrue(ChatListPresenter.looksLikeNeedsMe("what's urgent"));
        assertFalse(ChatListPresenter.looksLikeNeedsMe("send that"));
    }

    @Test
    public void presenterSourcesStayAndroidImportFree() throws Exception {
        for (String leaf : List.of(
                "ChatListPresenter.java",
                "ProposalFlowPresenter.java",
                "PersonTimeline.java",
                "ChatBubble.java")) {
            Path path = presenterSource(leaf);
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            assertFalse(leaf + " imported android", text.contains("import android."));
        }
    }

    @Test
    public void emptyFallbackAndJoinStatusMatchPreviousHelpers() {
        assertEquals("fallback", ChatListPresenter.emptyFallback("  ", "fallback"));
        assertEquals("hi", ChatListPresenter.emptyFallback(" hi ", "fallback"));
        assertEquals("Ready", ChatListPresenter.joinStatus("Ready", "  "));
        assertEquals("Ready · online", ChatListPresenter.joinStatus("Ready", "online"));
        assertEquals("session", ChatListPresenter.safeId("  ", "session"));
    }

    private static AttentionAssessment assessment(
            String personId,
            String leadEventId,
            AttentionLevel level,
            String reason,
            AssessmentSource source) {
        return new AttentionAssessment(
                "tick-" + leadEventId,
                personId,
                "com.whatsapp",
                leadEventId,
                1,
                0.7,
                level,
                reason,
                0.8,
                null,
                source);
    }

    private static Path presenterSource(String leaf) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path fromModule = cwd.resolve("src/main/java/com/textureflow/ui/chats").resolve(leaf);
        if (Files.isRegularFile(fromModule)) return fromModule;
        return cwd.resolve("app/src/main/java/com/textureflow/ui/chats").resolve(leaf);
    }

    private static StoredNotificationEvent event(
            String id,
            String sender,
            String packageName,
            String appLabel,
            String body,
            long updatedAt,
            String level,
            double score,
            Set<String> capabilities) {
        return new StoredNotificationEvent(
                id,
                "device",
                "key-" + id,
                packageName,
                appLabel,
                sender,
                sender,
                body,
                updatedAt,
                updatedAt,
                1,
                "ACTIVE",
                new ArrayList<>(capabilities).isEmpty() ? Set.of() : capabilities,
                "hash",
                "fp",
                score,
                level,
                "reason");
    }
}
