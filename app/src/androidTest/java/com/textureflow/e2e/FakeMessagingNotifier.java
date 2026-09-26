package com.textureflow.e2e;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Objects;

/**
 * Posts MessagingStyle notifications with a RemoteInput reply action from the
 * <em>test</em> process so they are not dropped as TextureFlow's own package.
 */
public final class FakeMessagingNotifier {
    public static final String CHANNEL_ID = "textureflow-e2e";
    public static final String REMOTE_INPUT_KEY = "e2e_reply";
    public static final int NOTIFICATION_ID = 7101;
    public static final String TAG_PREFIX = "tf-e2e-";

    private FakeMessagingNotifier() {}

    public static final class Posted {
        public final String tag;
        public final String token;
        public final String sender;
        public final String body;
        public final String conversation;
        public final int id;

        Posted(String tag, String token, String sender, String body, String conversation, int id) {
            this.tag = tag;
            this.token = token;
            this.sender = sender;
            this.body = body;
            this.conversation = conversation;
            this.id = id;
        }
    }

    public static void ensureChannel(Context testContext) {
        NotificationManager manager = manager(testContext);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "TextureFlow E2E", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Fake MessagingStyle posts for TextureFlow instrumentation");
        manager.createNotificationChannel(channel);
    }

    public static Posted postMessagingReply(Context testContext, String sender, String body) {
        Objects.requireNonNull(testContext, "testContext");
        ensureChannel(testContext);
        String token = TAG_PREFIX + Long.toHexString(System.nanoTime());
        String conversation = token;
        Notification notification = buildMessagingNotification(testContext, sender, body, conversation);
        manager(testContext).notify(token, NOTIFICATION_ID, notification);
        return new Posted(token, token, sender, body, conversation, NOTIFICATION_ID);
    }

    public static void cancel(Context testContext, Posted posted) {
        if (posted == null) return;
        manager(testContext).cancel(posted.tag, posted.id);
    }

    private static Notification buildMessagingNotification(
            Context testContext, String sender, String body, String conversation) {
        long now = System.currentTimeMillis();
        Notification.MessagingStyle style = messagingStyle(sender, body, conversation, now);
        PendingIntent replyIntent = PendingIntent.getBroadcast(
                testContext,
                conversation.hashCode(),
                new Intent(testContext, ReplyProbeReceiver.class)
                        .setAction("com.textureflow.e2e.REPLY")
                        .putExtra("token", conversation),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("Reply")
                .build();
        Notification.Action.Builder action = new Notification.Action.Builder(
                android.R.drawable.ic_menu_send, "Reply", replyIntent)
                .addRemoteInput(remoteInput);
        if (Build.VERSION.SDK_INT >= 28) {
            action.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY);
        }
        Notification.Builder builder = new Notification.Builder(testContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(sender)
                .setContentText(body)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .addAction(action.build())
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setGroup(conversation);
        }
        return builder.build();
    }

    @SuppressWarnings("deprecation")
    private static Notification.MessagingStyle messagingStyle(
            String sender, String body, String conversation, long now) {
        if (Build.VERSION.SDK_INT >= 28) {
            Person you = new Person.Builder().setName("You").build();
            Person them = new Person.Builder().setName(sender).setImportant(true).build();
            return new Notification.MessagingStyle(you)
                    .setConversationTitle(conversation)
                    .addMessage(new Notification.MessagingStyle.Message(body, now, them));
        }
        return new Notification.MessagingStyle("You")
                .setConversationTitle(conversation)
                .addMessage(body, now, sender);
    }

    private static NotificationManager manager(Context testContext) {
        NotificationManager manager =
                (NotificationManager) testContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("NotificationManager is unavailable");
        }
        return manager;
    }
}
