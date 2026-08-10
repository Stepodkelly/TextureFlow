package com.textureflow.notifications;

import android.app.Notification;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import com.textureflow.actions.NotificationActionInspector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NotificationNormalizer {
    private static final int MAX_TEXT_LENGTH = 4_000;

    public NormalizedNotification normalize(Context context, NotificationSnapshot snapshot, String deviceId) {
        Bundle extras = snapshot.getExtras();
        MessageParts messaging = messagingParts(extras);
        String title = firstNonBlank(
                messaging.sender,
                text(extras, Notification.EXTRA_TITLE),
                text(extras, Notification.EXTRA_TITLE_BIG),
                "Unknown sender");
        String body = firstNonBlank(
                messaging.body,
                text(extras, Notification.EXTRA_BIG_TEXT),
                text(extras, Notification.EXTRA_TEXT),
                lastTextLine(extras));
        String conversation = firstNonBlank(
                text(extras, Notification.EXTRA_CONVERSATION_TITLE), title);

        if (snapshot.getVisibility() == Notification.VISIBILITY_SECRET) {
            title = "Private notification";
            body = null;
            conversation = null;
        }

        title = clean(title);
        body = clean(body);
        conversation = clean(conversation);
        String appLabel = resolveAppLabel(context, snapshot.getPackageName());

        Notification.Action replyAction = NotificationActionInspector.findReplyAction(snapshot.getActions());
        String actionFingerprint = NotificationActionInspector.fingerprint(replyAction);
        Set<String> capabilities = new LinkedHashSet<>();
        if (replyAction != null) capabilities.add("REPLY");
        if (snapshot.isClearable()) {
            capabilities.add("DISMISS");
            capabilities.add("SNOOZE");
        }

        Priority priority = priority(body);
        String eventId = ContentFingerprint.eventId(
                deviceId, snapshot.getPackageName(), snapshot.getKey());
        String contentHash = ContentFingerprint.sha256(
                snapshot.getPackageName() + "\n" + nullToEmpty(title) + "\n" + nullToEmpty(conversation)
                        + "\n" + nullToEmpty(body) + "\n" + String.join(",", capabilities)
                        + "\n" + actionFingerprint);

        return new NormalizedNotification(
                eventId, deviceId, snapshot.getKey(), snapshot.getPackageName(), appLabel,
                title, conversation, body, snapshot.getPostTime(), capabilities, contentHash,
                actionFingerprint, priority.score, priority.level, priority.reason);
    }

    @SuppressWarnings("deprecation") // API 26/27 fallbacks are required by the declared minSdk.
    private static MessageParts messagingParts(Bundle extras) {
        // Platform bundle decoding became public in API 30. Older releases still
        // expose title/text fallbacks, which normalize() reads below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return MessageParts.EMPTY;
        try {
            Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (bundles == null || bundles.length == 0) return MessageParts.EMPTY;
            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
            if (messages == null || messages.isEmpty()) return MessageParts.EMPTY;
            Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
            String sender = null;
            if (Build.VERSION.SDK_INT >= 28) {
                Person person = latest.getSenderPerson();
                if (person != null && person.getName() != null) sender = person.getName().toString();
            }
            if (sender == null && latest.getSender() != null) sender = latest.getSender().toString();
            String body = latest.getText() == null ? null : latest.getText().toString();
            return new MessageParts(sender, body);
        } catch (RuntimeException malformedStyle) {
            return MessageParts.EMPTY;
        }
    }

    private static String lastTextLine(Bundle extras) {
        try {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines == null) return null;
            for (int index = lines.length - 1; index >= 0; index--) {
                if (lines[index] != null && lines[index].length() > 0) return lines[index].toString();
            }
        } catch (RuntimeException ignored) {
            // A malformed application Bundle must not terminate notification ingestion.
        }
        return null;
    }

    private static String text(Bundle extras, String key) {
        try {
            CharSequence value = extras.getCharSequence(key);
            return value == null ? null : value.toString();
        } catch (RuntimeException malformedValue) {
            return null;
        }
    }

    private static String resolveAppLabel(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            CharSequence label = manager.getApplicationLabel(info);
            String cleaned = label == null ? null : clean(label.toString());
            return cleaned == null ? packageName : cleaned;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return packageName;
        }
    }

    private static Priority priority(String body) {
        String normalized = nullToEmpty(body).toLowerCase(Locale.US);
        if (normalized.matches(".*\\b(urgent|asap|emergency|locked out|downstairs|outside)\\b.*")) {
            return new Priority(0.90, "URGENT", "The message contains an immediate-attention signal.");
        }
        if (normalized.contains("?")) {
            return new Priority(0.72, "IMPORTANT", "A direct question may need a response.");
        }
        return new Priority(0.50, "NORMAL", "A recent notification is available for review.");
    }

    private static String clean(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_TEXT_LENGTH));
        for (int i = 0; i < value.length() && result.length() < MAX_TEXT_LENGTH; i++) {
            char character = value.charAt(i);
            if (character == '\n' || character == '\t' || !Character.isISOControl(character)) {
                result.append(character);
            }
        }
        String trimmed = result.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) return candidate;
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class MessageParts {
        static final MessageParts EMPTY = new MessageParts(null, null);
        final String sender;
        final String body;
        MessageParts(String sender, String body) { this.sender = sender; this.body = body; }
    }

    private static final class Priority {
        final double score;
        final String level;
        final String reason;
        Priority(double score, String level, String reason) {
            this.score = score;
            this.level = level;
            this.reason = reason;
        }
    }
}
