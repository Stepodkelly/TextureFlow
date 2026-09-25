package com.textureflow.intelligence.shield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class TokenBudgetTest {
    private static final long NOW = 2_000_000_000_000L;

    @Test
    public void heuristicIsCeilCodePointsOverFour() {
        TokenEstimator estimator = CharHeuristicTokenEstimator.INSTANCE;
        assertEquals(0, estimator.estimateTokens(""));
        assertEquals(1, estimator.estimateTokens("abcd"));
        assertEquals(2, estimator.estimateTokens("abcde"));
        assertEquals(1, estimator.estimateTokens("\uD83D\uDE00\uD83D\uDE00"));
    }

    @Test
    public void truncateToTokensNeverSplitsACodePoint() {
        String text = "ab\uD83D\uDE00cd";
        String kept = TokenBudget.truncateToTokens(text, 1, CharHeuristicTokenEstimator.INSTANCE);
        assertFalse(kept.isEmpty());
        assertFalse(Character.isHighSurrogate(kept.charAt(kept.length() - 1)));
        assertTrue(CharHeuristicTokenEstimator.INSTANCE.estimateTokens(kept) <= 1);
    }

    @Test
    public void trimsOldestEventsFirstAndReportsDroppedTokens() {
        TokenBudget tight = new TokenBudget(20, 8, 0, 30);
        ContextShield shield = new ContextShield(ContextLimits.DEFAULT, tight, CharHeuristicTokenEstimator.INSTANCE);
        List<ShieldEvent> events = new ArrayList<>();
        events.add(event("newest", "newest message is kept in full here", NOW - 60_000L));
        events.add(event("oldest", "oldest message should be dropped or trimmed first " + "z".repeat(80), NOW - 10 * 60_000L));

        ShieldedContext context = shield.shield(new ShieldRequest(
                "person_sam", "Sam", "friend", "WhatsApp", "", events, NOW));

        assertTrue(context.droppedTokens() > 0);
        assertTrue(context.contextOverflow());
        assertTrue(context.inputTokens() > 0);
        assertTrue(context.getEvents().size() >= 1);
        assertEquals(1, context.getEvents().get(0).getAgeMinutes());
    }

    private static ShieldEvent event(String id, String body, long postedAt) {
        return new ShieldEvent(
                id, 1, "person_sam", "com.whatsapp", "WhatsApp", "Sam",
                body, postedAt, ShieldEvent.STATUS_ACTIVE, "key");
    }
}
