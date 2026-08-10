package com.textureflow.actions;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.os.Build;

import com.textureflow.notifications.ContentFingerprint;

import java.util.Locale;

public final class NotificationActionInspector {
    private NotificationActionInspector() {}

    public static Notification.Action findReplyAction(Notification.Action[] actions) {
        if (actions == null) return null;
        Notification.Action fallback = null;
        for (Notification.Action action : actions) {
            if (action == null || action.actionIntent == null) continue;
            RemoteInput[] inputs = action.getRemoteInputs();
            if (!hasFreeFormInput(inputs)) continue;
            if (isSemanticReply(action) || titleLooksLikeReply(action.title)) return action;
            if (fallback == null) fallback = action;
        }
        return fallback;
    }

    public static boolean hasFreeFormInput(RemoteInput[] inputs) {
        if (inputs == null) return false;
        for (RemoteInput input : inputs) {
            if (input != null && input.getAllowFreeFormInput()) return true;
        }
        return false;
    }

    public static String fingerprint(Notification.Action action) {
        if (action == null) return "none";
        StringBuilder material = new StringBuilder();
        material.append(action.title == null ? "" : action.title).append('|');
        if (Build.VERSION.SDK_INT >= 28) material.append(action.getSemanticAction());
        PendingIntent pendingIntent = action.actionIntent;
        if (pendingIntent != null) {
            material.append('|').append(pendingIntent.getCreatorPackage());
            // A changed live token invalidates old proposals even if its visible label is unchanged.
            material.append('|').append(pendingIntent.hashCode());
        }
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs != null) {
            for (RemoteInput input : inputs) {
                if (input == null) continue;
                material.append('|').append(input.getResultKey())
                        .append(':').append(input.getAllowFreeFormInput());
            }
        }
        return ContentFingerprint.sha256(material.toString());
    }

    private static boolean isSemanticReply(Notification.Action action) {
        return Build.VERSION.SDK_INT >= 28
                && action.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY;
    }

    private static boolean titleLooksLikeReply(CharSequence title) {
        if (title == null) return false;
        String normalized = title.toString().toLowerCase(Locale.US);
        return normalized.contains("reply") || normalized.contains("respond");
    }
}
