package com.textureflow.notifications;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NormalizedNotification {
    private final String eventId;
    private final String deviceId;
    private final String notificationKey;
    private final String packageName;
    private final String appLabel;
    private final String senderName;
    private final String conversationLabel;
    private final String body;
    private final long postedAt;
    private final Set<String> capabilities;
    private final String contentHash;
    private final String actionFingerprint;
    private final double priorityScore;
    private final String priorityLevel;
    private final String priorityReason;

    public NormalizedNotification(
            String eventId,
            String deviceId,
            String notificationKey,
            String packageName,
            String appLabel,
            String senderName,
            String conversationLabel,
            String body,
            long postedAt,
            Set<String> capabilities,
            String contentHash,
            String actionFingerprint,
            double priorityScore,
            String priorityLevel,
            String priorityReason) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.notificationKey = notificationKey;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.senderName = senderName;
        this.conversationLabel = conversationLabel;
        this.body = body;
        this.postedAt = postedAt;
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        this.contentHash = contentHash;
        this.actionFingerprint = actionFingerprint;
        this.priorityScore = priorityScore;
        this.priorityLevel = priorityLevel;
        this.priorityReason = priorityReason;
    }

    public String getEventId() { return eventId; }
    public String getDeviceId() { return deviceId; }
    public String getNotificationKey() { return notificationKey; }
    public String getPackageName() { return packageName; }
    public String getAppLabel() { return appLabel; }
    public String getSenderName() { return senderName; }
    public String getConversationLabel() { return conversationLabel; }
    public String getBody() { return body; }
    public long getPostedAt() { return postedAt; }
    public Set<String> getCapabilities() { return capabilities; }
    public String getContentHash() { return contentHash; }
    public String getActionFingerprint() { return actionFingerprint; }
    public double getPriorityScore() { return priorityScore; }
    public String getPriorityLevel() { return priorityLevel; }
    public String getPriorityReason() { return priorityReason; }
}
