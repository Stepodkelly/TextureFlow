package com.textureflow.actions;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.textureflow.data.ActionReceiptStore;
import com.textureflow.data.NotificationRepository;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.notifications.ContentFingerprint;
import com.textureflow.policy.CommandPolicy;
import com.textureflow.policy.PolicyDecision;

import java.time.Instant;

public final class NotificationActionExecutor {
    private final Context context;
    private final String deviceId;
    private final LiveActionRegistry registry;
    private final NotificationRepository repository;
    private final ActionReceiptStore receipts;
    private final CommandPolicy policy;

    public NotificationActionExecutor(
            Context context,
            String deviceId,
            LiveActionRegistry registry,
            NotificationRepository repository,
            ActionReceiptStore receipts,
            CommandPolicy policy) {
        this.context = context.getApplicationContext();
        this.deviceId = deviceId;
        this.registry = registry;
        this.repository = repository;
        this.receipts = receipts;
        this.policy = policy;
    }

    public synchronized ActionReceipt execute(
            ActionCommand command, ConfirmedProposal confirmation, NotificationControl control) {
        long now = System.currentTimeMillis();
        ActionReceiptStore.Reservation reservation = receipts.reserve(command, now);
        if (reservation.getState() == ActionReceiptStore.ReservationState.COMPLETED
                && reservation.getExistingReceipt() != null) {
            return reservation.getExistingReceipt();
        }
        if (reservation.getState() == ActionReceiptStore.ReservationState.UNCERTAIN) {
            ActionReceipt refused = failure(
                    reservation.getReservedCommandId(), ActionErrorCode.DUPLICATE_COMMAND,
                    "A previous dispatch may have crossed the app boundary; TextureFlow will not replay it.");
            receipts.complete(reservation.getReservedCommandId(), refused, now);
            return refused;
        }

        PolicyDecision decision = policy.evaluate(command, confirmation, repository, registry, now);
        if (!decision.isAllowed()) {
            ActionReceipt failure = failure(command.getCommandId(), decision.getErrorCode(), decision.getMessage());
            receipts.complete(reservation.getReservedCommandId(), failure, now);
            return failure;
        }
        if (control == null || !control.isAvailable()) {
            ActionReceipt failure = failure(
                    command.getCommandId(), ActionErrorCode.DEVICE_OFFLINE,
                    "Android notification control is disconnected.");
            receipts.complete(reservation.getReservedCommandId(), failure, now);
            return failure;
        }

        LiveActionRegistry.Entry entry = registry.get(command.getEventId());
        try {
            if (command.getActionType() == ActionType.REPLY) {
                dispatchReply(entry.getReplyAction(), command.stringPayload("message"));
            } else if (command.getActionType() == ActionType.DISMISS) {
                control.dismiss(entry.getNotificationKey());
            } else if (command.getActionType() == ActionType.SNOOZE) {
                control.snooze(entry.getNotificationKey(), command.longPayload("minutes", 1) * 60_000L);
            }
            StoredNotificationEvent event = repository.getEvent(command.getEventId());
            String action = command.getActionType().name().toLowerCase();
            ActionReceipt dispatched = new ActionReceipt(
                    receiptId(command.getCommandId()), command.getCommandId(), deviceId, "DISPATCHED", null,
                    capitalize(action) + " dispatched through " + (event == null ? entry.getPackageName() : event.getAppLabel()) + ".",
                    Instant.now().toString(), "ACTION_DISPATCHED", traceId(command.getCommandId()));
            receipts.complete(reservation.getReservedCommandId(), dispatched, now);
            return dispatched;
        } catch (PendingIntent.CanceledException cancelled) {
            ActionReceipt failure = failure(
                    command.getCommandId(), ActionErrorCode.PENDING_INTENT_CANCELLED,
                    "The messaging app cancelled its live reply action.");
            receipts.complete(reservation.getReservedCommandId(), failure, now);
            return failure;
        } catch (RuntimeException platformFailure) {
            ActionReceipt failure = failure(
                    command.getCommandId(), ActionErrorCode.POLICY_BLOCKED,
                    "Android rejected the notification action: " + safeMessage(platformFailure));
            receipts.complete(reservation.getReservedCommandId(), failure, now);
            return failure;
        }
    }

    private void dispatchReply(Notification.Action action, String message) throws PendingIntent.CanceledException {
        if (action == null || action.actionIntent == null) {
            throw new IllegalStateException("Reply action disappeared after validation");
        }
        RemoteInput[] remoteInputs = action.getRemoteInputs();
        if (!NotificationActionInspector.hasFreeFormInput(remoteInputs)) {
            throw new IllegalStateException("Reply RemoteInput disappeared after validation");
        }
        Bundle results = new Bundle();
        for (RemoteInput input : remoteInputs) {
            if (input != null && input.getAllowFreeFormInput()) {
                results.putCharSequence(input.getResultKey(), message);
            }
        }
        Intent fillIn = new Intent();
        fillIn.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results);
        action.actionIntent.send(context, 0, fillIn);
    }

    private ActionReceipt failure(String commandId, ActionErrorCode error, String message) {
        String status;
        if (error == ActionErrorCode.COMMAND_EXPIRED) {
            status = "EXPIRED";
        } else if (error == ActionErrorCode.EVENT_CHANGED
                || error == ActionErrorCode.NOTIFICATION_GONE
                || error == ActionErrorCode.ACTION_HANDLE_CHANGED) {
            status = "STALE";
        } else {
            status = "FAILED";
        }
        return new ActionReceipt(
                receiptId(commandId), commandId, deviceId, status, error, message,
                Instant.now().toString(), "ACTION_FAILED", traceId(commandId));
    }

    private static String receiptId(String commandId) {
        return "receipt_" + ContentFingerprint.sha256(commandId).substring(0, 24);
    }

    private static String traceId(String commandId) {
        return "trace_" + ContentFingerprint.sha256("trace:" + commandId).substring(0, 24);
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static String safeMessage(RuntimeException error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }
}
