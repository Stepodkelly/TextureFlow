package com.textureflow.connection;

import android.content.Context;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ActionReceipt;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.notifications.TextureNotificationListenerService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phone-side proposal/confirm path that never calls Convex or VoiceOS.
 * Used while those surfaces are stubbed.
 */
public final class LocalCoreActionClient {
    private static final class Pending {
        final CoreActionClient.Proposal proposal;
        final StoredNotificationEvent event;
        final Map<String, Object> payload;

        Pending(CoreActionClient.Proposal proposal, StoredNotificationEvent event,
                Map<String, Object> payload) {
            this.proposal = proposal;
            this.event = event;
            this.payload = new LinkedHashMap<>(payload);
        }
    }

    private final Context context;
    private final ConnectionConfig config;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    public LocalCoreActionClient(Context context, ConnectionConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
    }

    public CoreActionClient.Proposal create(
            StoredNotificationEvent event, ActionType actionType, Map<String, Object> payload) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String proposalId = "local_proposal_" + nonce;
        String sessionId = "local_session_" + nonce;
        String preview = preview(event, actionType, payload);
        String reply = actionType == ActionType.REPLY
                ? String.valueOf(payload.getOrDefault("message", "")).trim() : null;
        CoreActionClient.Proposal proposal = new CoreActionClient.Proposal(
                proposalId, sessionId, 1, actionType, preview, reply);
        pending.put(proposalId, new Pending(proposal, event, payload));
        return proposal;
    }

    public CoreActionClient.Proposal reviseReply(
            CoreActionClient.Proposal current, StoredNotificationEvent event, String message) {
        Pending existing = pending.get(current.proposalId());
        if (existing == null) {
            throw new IllegalStateException("Local proposal is no longer available");
        }
        Map<String, Object> payload = new LinkedHashMap<>(existing.payload);
        payload.put("message", message.trim());
        String preview = preview(event, ActionType.REPLY, payload);
        CoreActionClient.Proposal revised = new CoreActionClient.Proposal(
                current.proposalId(),
                current.sessionId(),
                current.revision() + 1,
                ActionType.REPLY,
                preview,
                message.trim());
        pending.put(current.proposalId(), new Pending(revised, event, payload));
        return revised;
    }

    public CoreActionClient.Confirmation confirm(CoreActionClient.Proposal proposal) {
        Pending existing = pending.remove(proposal.proposalId());
        if (existing == null) {
            throw new IllegalStateException("Local proposal is no longer available");
        }
        String commandId = "local_command_" + UUID.randomUUID().toString().replace("-", "");
        String now = Instant.now().toString();
        String expires = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
        RemoteCommand command = new RemoteCommand(
                1,
                commandId,
                config.ownerId(),
                proposal.proposalId(),
                config.deviceId(),
                existing.event.getEventId(),
                existing.event.getVersion(),
                proposal.actionType(),
                existing.payload,
                "local:" + proposal.proposalId(),
                "CLAIMED",
                "LOCAL_STUB",
                "local_trace_" + proposal.proposalId(),
                now,
                expires);
        RemoteProposal remoteProposal = new RemoteProposal(
                proposal.proposalId(),
                config.ownerId(),
                config.deviceId(),
                existing.event.getEventId(),
                existing.event.getVersion(),
                proposal.actionType(),
                existing.payload,
                "CONFIRMED",
                now,
                expires);
        ActionReceipt actionReceipt = TextureNotificationListenerService.executeConfirmed(
                context, command.toLocalClaimedCommand(), remoteProposal.toConfirmedProposal());
        CoreActionClient.Receipt receipt = new CoreActionClient.Receipt(
                actionReceipt.getReceiptId(),
                actionReceipt.getStatus(),
                actionReceipt.getMessage() == null
                        ? "Local stub executed on-device."
                        : actionReceipt.getMessage());
        return new CoreActionClient.Confirmation(commandId, receipt);
    }

    public void cancel(CoreActionClient.Proposal proposal) {
        if (proposal != null) pending.remove(proposal.proposalId());
    }

    private static String preview(
            StoredNotificationEvent event, ActionType type, Map<String, Object> payload) {
        String who = event.getSenderName() == null || event.getSenderName().isBlank()
                ? "them" : event.getSenderName().trim();
        return switch (type) {
            case REPLY -> "Reply to " + who + ": "
                    + String.valueOf(payload.getOrDefault("message", "")).trim();
            case SNOOZE -> "Snooze " + who + " for "
                    + payload.getOrDefault("minutes", 60) + " minutes";
            case DISMISS -> "Dismiss the notification from " + who;
        };
    }
}
