package com.textureflow.ui.chats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.intelligence.api.AssessmentSource;
import com.textureflow.intelligence.api.ProposalDraft;
import com.textureflow.intelligence.api.ReplyTone;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public final class ProposalFlowPresenterTest {
    @Test
    public void offerAcceptsFreshDraft() {
        ProposalFlowPresenter presenter = new ProposalFlowPresenter();
        ProposalDraft draft = draft("p1", "e1", 3, "I'm coming", 10_000L);
        assertEquals(ProposalFlowPresenter.OfferResult.ACCEPTED, presenter.offer(draft, 3, 1_000L));
        assertEquals("p1", presenter.active().getProposalId());
        assertEquals("Reply to Sam: I'm coming", ProposalFlowPresenter.readBackText(draft));
    }

    @Test
    public void offerMarksStaleOnVersionMismatch() {
        ProposalFlowPresenter presenter = new ProposalFlowPresenter();
        ProposalDraft draft = draft("p1", "e1", 2, "I'm coming", 10_000L);
        assertEquals(ProposalFlowPresenter.OfferResult.STALE, presenter.offer(draft, 3, 1_000L));
        assertNull(presenter.active());
        assertNotNull(presenter.pending("p1"));
    }

    @Test
    public void offerMarksStaleWhenExpired() {
        ProposalFlowPresenter presenter = new ProposalFlowPresenter();
        ProposalDraft draft = draft("p1", "e1", 1, "I'm coming", 500L);
        assertEquals(ProposalFlowPresenter.OfferResult.STALE, presenter.offer(draft, 1, 500L));
        assertTrue(ProposalFlowPresenter.isStale(draft, 1, 500L));
        assertFalse(ProposalFlowPresenter.isStale(draft, 1, 499L));
    }

    @Test
    public void invalidateClearsActiveDraftAndKeepsReason() {
        ProposalFlowPresenter presenter = new ProposalFlowPresenter();
        ProposalDraft draft = draft("p1", "e1", 1, "I'm coming", 10_000L);
        presenter.offer(draft, 1, 1L);
        assertTrue(presenter.invalidate("p1", "EVENT_REMOVED"));
        assertNull(presenter.active());
        assertEquals("EVENT_REMOVED", presenter.lastInvalidationReason());
        assertFalse(presenter.invalidate("missing", "gone"));
    }

    @Test
    public void confirmBuildsConfirmedProposalForCommandPolicy() {
        ProposalDraft draft = draft("p1", "evt-9", 4, "On my way",
                Instant.parse("2026-09-26T00:00:09Z").toEpochMilli());
        Map<String, Object> payload = ProposalFlowPresenter.confirmPayload(draft, "On my way now");
        ConfirmedProposal confirmation = ProposalFlowPresenter.toConfirmedProposal(
                draft, "owner-1", "device-1", "2026-09-26T00:00:00Z", payload);
        assertEquals("p1", confirmation.getProposalId());
        assertEquals("owner-1", confirmation.getOwnerId());
        assertEquals("device-1", confirmation.getTargetDeviceId());
        assertEquals("evt-9", confirmation.getEventId());
        assertEquals(4, confirmation.getExpectedEventVersion());
        assertEquals(ActionType.REPLY, confirmation.getActionType());
        assertEquals("CONFIRMED", confirmation.getStatus());
        assertEquals("On my way now", confirmation.stringPayload("message"));
        assertEquals("2026-09-26T00:00:09Z", confirmation.getExpiresAt());
    }

    @Test
    public void snoozePayloadUsesDefaultWindow() {
        ProposalDraft draft = new ProposalDraft(
                "p2", "e2", 1, "com.whatsapp", "sam", "Sam",
                ActionType.SNOOZE, "", ReplyTone.NEUTRAL, "Snooze Sam for 60 minutes",
                AssessmentSource.DETERMINISTIC, 0.7, List.of(), "hash", 0L);
        Map<String, Object> payload = ProposalFlowPresenter.confirmPayload(draft);
        assertEquals(60L, payload.get("minutes"));
        assertEquals("Snooze Sam for 60 minutes", ProposalFlowPresenter.readBackText(draft));
    }

    private static ProposalDraft draft(
            String proposalId, String eventId, int version, String reply, long expiresAt) {
        return new ProposalDraft(
                proposalId,
                eventId,
                version,
                "com.whatsapp",
                "sam",
                "Sam",
                ActionType.REPLY,
                reply,
                ReplyTone.DIRECT,
                "Reply to Sam: " + reply,
                AssessmentSource.DETERMINISTIC,
                0.8,
                List.of(),
                "hash",
                expiresAt);
    }
}
