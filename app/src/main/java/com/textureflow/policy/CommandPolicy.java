package com.textureflow.policy;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionErrorCode;
import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.actions.LiveActionRegistry;
import com.textureflow.data.NotificationRepository;
import com.textureflow.data.StoredNotificationEvent;

public final class CommandPolicy {
    private final String expectedOwnerId;
    private final String localDeviceId;

    public CommandPolicy(String expectedOwnerId, String localDeviceId) {
        this.expectedOwnerId = expectedOwnerId;
        this.localDeviceId = localDeviceId;
    }

    public PolicyDecision evaluate(
            ActionCommand command,
            ConfirmedProposal confirmation,
            NotificationRepository repository,
            LiveActionRegistry registry,
            long now) {
        if (command == null || confirmation == null) {
            return deny(ActionErrorCode.UNAUTHORIZED, "A trusted confirmed proposal is required.");
        }
        if (command.getContractVersion() != ActionCommand.CONTRACT_VERSION) {
            return deny(ActionErrorCode.POLICY_BLOCKED, "Unsupported command contract version.");
        }
        if (!"QUEUED".equals(command.getStatus()) && !"CLAIMED".equals(command.getStatus())) {
            return deny(ActionErrorCode.POLICY_BLOCKED, "Command is not in an executable state.");
        }
        if (!same(expectedOwnerId, command.getOwnerId())
                || !same(localDeviceId, command.getTargetDeviceId())) {
            return deny(ActionErrorCode.UNAUTHORIZED, "Command is not owned by this user and device.");
        }
        if (blank(command.getCommandId()) || blank(command.getProposalId()) || blank(command.getIdempotencyKey())) {
            return deny(ActionErrorCode.POLICY_BLOCKED, "Command identity is incomplete.");
        }
        if (CommandFreshness.isExpired(command.getExpiresAt(), now)) {
            return deny(ActionErrorCode.COMMAND_EXPIRED, "Command expired before device execution.");
        }
        if (CommandFreshness.isExpired(confirmation.getExpiresAt(), now)) {
            return deny(ActionErrorCode.COMMAND_EXPIRED, "Confirmation grant expired before execution.");
        }
        if (!confirmationMatches(command, confirmation)) {
            return deny(ActionErrorCode.UNAUTHORIZED, "Confirmation does not authorize this exact command.");
        }

        StoredNotificationEvent event = repository.getEvent(command.getEventId());
        if (event == null || !event.isLive()) {
            return deny(ActionErrorCode.NOTIFICATION_GONE, "The notification is no longer active.");
        }
        if (event.getVersion() != command.getExpectedEventVersion()) {
            return deny(ActionErrorCode.EVENT_CHANGED, "The notification changed after the proposal was prepared.");
        }
        LiveActionRegistry.Entry live = registry.get(command.getEventId());
        if (live == null) {
            return deny(ActionErrorCode.NOTIFICATION_GONE, "No live Android action handle is available.");
        }
        if (live.getEventVersion() != event.getVersion()) {
            return deny(ActionErrorCode.EVENT_CHANGED, "The live notification version does not match storage.");
        }
        if (!same(live.getActionFingerprint(), event.getActionFingerprint())) {
            return deny(ActionErrorCode.ACTION_HANDLE_CHANGED, "The Android action handle changed.");
        }

        ActionType type = command.getActionType();
        if (type == ActionType.REPLY) {
            String message = command.stringPayload("message");
            if (!event.hasCapability("REPLY") || live.getReplyAction() == null) {
                return deny(ActionErrorCode.REPLY_NOT_SUPPORTED, "This notification no longer supports replies.");
            }
            if (blank(message) || message.trim().length() > 4_000) {
                return deny(ActionErrorCode.POLICY_BLOCKED, "Reply text must contain 1 to 4000 characters.");
            }
        } else if (type == ActionType.DISMISS) {
            if (!event.hasCapability("DISMISS") || !live.isDismissSupported()) {
                return deny(ActionErrorCode.POLICY_BLOCKED, "This notification cannot be dismissed.");
            }
        } else if (type == ActionType.SNOOZE) {
            long minutes = command.longPayload("minutes", -1);
            if (!event.hasCapability("SNOOZE") || !live.isSnoozeSupported()) {
                return deny(ActionErrorCode.POLICY_BLOCKED, "This notification cannot be snoozed.");
            }
            if (minutes < 1 || minutes > 24 * 60) {
                return deny(ActionErrorCode.POLICY_BLOCKED, "Snooze duration must be between 1 and 1440 minutes.");
            }
        } else {
            return deny(ActionErrorCode.POLICY_BLOCKED, "Unsupported action type.");
        }
        return PolicyDecision.allow();
    }

    private static boolean confirmationMatches(ActionCommand command, ConfirmedProposal confirmation) {
        boolean identityMatches = same(command.getProposalId(), confirmation.getProposalId())
                && same(command.getOwnerId(), confirmation.getOwnerId())
                && same(command.getTargetDeviceId(), confirmation.getTargetDeviceId())
                && same(command.getEventId(), confirmation.getEventId())
                && command.getExpectedEventVersion() == confirmation.getExpectedEventVersion()
                && command.getActionType() == confirmation.getActionType();
        if (!identityMatches) return false;
        if (command.getActionType() == ActionType.REPLY) {
            return same(command.stringPayload("message"), confirmation.stringPayload("message"));
        }
        if (command.getActionType() == ActionType.SNOOZE) {
            return command.longPayload("minutes", -1) == confirmation.longPayload("minutes", -2);
        }
        return true;
    }

    private static PolicyDecision deny(ActionErrorCode code, String message) {
        return PolicyDecision.deny(code, message);
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
