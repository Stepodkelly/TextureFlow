package com.textureflow.ui.chats;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.intelligence.api.ProposalDraft;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure proposal-draft bookkeeping for Stream I. No Android imports.
 * Confirmation evidence is built here; execution stays on the existing
 * {@code CommandPolicy} / {@code LocalCoreActionClient} path.
 */
public final class ProposalFlowPresenter {
    public enum OfferResult {
        ACCEPTED,
        STALE,
        IGNORED
    }

    private final Map<String, ProposalDraft> pending = new LinkedHashMap<>();
    private ProposalDraft active;
    private String lastInvalidationReason = "";

    public OfferResult offer(ProposalDraft draft, int currentEventVersion, long nowMillis) {
        if (draft == null) return OfferResult.IGNORED;
        pending.put(draft.getProposalId(), draft);
        if (isStale(draft, currentEventVersion, nowMillis)) {
            if (active != null && draft.getProposalId().equals(active.getProposalId())) {
                active = null;
            }
            return OfferResult.STALE;
        }
        active = draft;
        return OfferResult.ACCEPTED;
    }

    public boolean invalidate(String proposalId, String reason) {
        lastInvalidationReason = reason == null ? "" : reason;
        if (proposalId == null || proposalId.isEmpty()) return false;
        ProposalDraft removed = pending.remove(proposalId);
        boolean wasActive = active != null && proposalId.equals(active.getProposalId());
        if (wasActive) active = null;
        return removed != null || wasActive;
    }

    public void clearActive() {
        if (active != null) pending.remove(active.getProposalId());
        active = null;
    }

    public void clearAll() {
        pending.clear();
        active = null;
        lastInvalidationReason = "";
    }

    public ProposalDraft active() {
        return active;
    }

    public ProposalDraft pending(String proposalId) {
        return pending.get(proposalId);
    }

    public Map<String, ProposalDraft> pendingSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pending));
    }

    public String lastInvalidationReason() {
        return lastInvalidationReason;
    }

    public static boolean isStale(ProposalDraft draft, int currentEventVersion, long nowMillis) {
        if (draft == null) return true;
        if (draft.getEventVersion() != currentEventVersion) return true;
        long expires = draft.getExpiresAtMillis();
        return expires > 0 && nowMillis >= expires;
    }

    public static String readBackText(ProposalDraft draft) {
        if (draft == null) return "";
        String preview = draft.getSpokenPreview();
        if (preview != null && !preview.trim().isEmpty()) return preview.trim();
        if (draft.getActionType() == ActionType.REPLY) {
            String reply = draft.getReplyText() == null ? "" : draft.getReplyText().trim();
            return reply.isEmpty() ? "Proposed reply" : "Reply: " + reply;
        }
        if (draft.getActionType() == ActionType.SNOOZE) {
            return "Snooze this notification.";
        }
        return "Dismiss this notification.";
    }

    public static Map<String, Object> confirmPayload(ProposalDraft draft) {
        Objects.requireNonNull(draft, "draft");
        Map<String, Object> payload = new LinkedHashMap<>();
        if (draft.getActionType() == ActionType.REPLY) {
            payload.put("message", draft.getReplyText() == null ? "" : draft.getReplyText());
        } else if (draft.getActionType() == ActionType.SNOOZE) {
            payload.put("minutes", 60L);
        }
        return payload;
    }

    public static Map<String, Object> confirmPayload(ProposalDraft draft, String editedReply) {
        Map<String, Object> payload = confirmPayload(draft);
        if (draft.getActionType() == ActionType.REPLY && editedReply != null) {
            payload.put("message", editedReply.trim());
        }
        return payload;
    }

    /**
     * Builds trusted confirmation evidence for {@code CommandPolicy}.
     * The engine draft is never executed directly.
     */
    public static ConfirmedProposal toConfirmedProposal(
            ProposalDraft draft,
            String ownerId,
            String targetDeviceId,
            String confirmedAt,
            Map<String, Object> payload) {
        Objects.requireNonNull(draft, "draft");
        String expiresAt = draft.getExpiresAtMillis() > 0
                ? Instant.ofEpochMilli(draft.getExpiresAtMillis()).toString()
                : Instant.parse(confirmedAt).plusSeconds(10 * 60).toString();
        return new ConfirmedProposal(
                draft.getProposalId(),
                ownerId,
                targetDeviceId,
                draft.getEventId(),
                draft.getEventVersion(),
                draft.getActionType(),
                payload == null ? confirmPayload(draft) : payload,
                "CONFIRMED",
                confirmedAt,
                expiresAt);
    }
}
