package com.textureflow.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class StoredNotificationEvent {
    private final String eventId;
    private final String deviceId;
    private final String notificationKey;
    private final String packageName;
    private final String appLabel;
    private final String senderName;
    private final String conversationLabel;
    private final String body;
    private final long postedAt;
    private final long updatedAt;
    private final int version;
    private final String status;
    private final Set<String> capabilities;
    private final String contentHash;
    private final String actionFingerprint;
    private final double priorityScore;
    private final String priorityLevel;
    private final String priorityReason;

    public StoredNotificationEvent(
            String eventId,
            String deviceId,
            String notificationKey,
            String packageName,
            String appLabel,
            String senderName,
            String conversationLabel,
            String body,
            long postedAt,
            long updatedAt,
            int version,
            String status,
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
        this.updatedAt = updatedAt;
        this.version = version;
        this.status = status;
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
    public long getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
    public String getStatus() { return status; }
    public Set<String> getCapabilities() { return capabilities; }
    public String getContentHash() { return contentHash; }
    public String getActionFingerprint() { return actionFingerprint; }
    public double getPriorityScore() { return priorityScore; }
    public String getPriorityLevel() { return priorityLevel; }
    public String getPriorityReason() { return priorityReason; }

    public boolean isLive() {
        return "ACTIVE".equals(status) || "UPDATED".equals(status);
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public JSONObject toContractJson() throws JSONException {
        JSONObject app = new JSONObject();
        app.put("packageName", packageName);
        app.put("label", appLabel);
        JSONObject sender = new JSONObject();
        sender.put("displayName", senderName);
        JSONObject priority = new JSONObject();
        priority.put("score", priorityScore);
        priority.put("level", priorityLevel);
        priority.put("reason", priorityReason);

        JSONObject json = new JSONObject();
        json.put("contractVersion", 1);
        json.put("eventId", eventId);
        json.put("deviceId", deviceId);
        json.put("app", app);
        json.put("sender", sender);
        if (conversationLabel != null) json.put("conversationLabel", conversationLabel);
        if (body != null) json.put("body", body);
        json.put("postedAt", Instant.ofEpochMilli(postedAt).toString());
        json.put("updatedAt", Instant.ofEpochMilli(updatedAt).toString());
        json.put("version", version);
        json.put("status", status);
        json.put("capabilities", new JSONArray(capabilities));
        json.put("priority", priority);
        return json;
    }
}
