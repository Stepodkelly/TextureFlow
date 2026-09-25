package com.textureflow.intelligence.policy;

import com.textureflow.actions.ActionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * I4 binding: {@code (eventId, eventVersion, packageName, recipient, actionType, payloadHash)}.
 * Any field change invalidates confirmation.
 */
public final class ProposalBinding {
    private final String eventId;
    private final int eventVersion;
    private final String packageName;
    private final String recipient;
    private final String actionType;
    private final String payloadHash;

    public ProposalBinding(
            String eventId,
            int eventVersion,
            String packageName,
            String recipient,
            String actionType,
            String payloadHash) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventVersion = eventVersion;
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash");
    }

    public ProposalBinding(
            String eventId,
            int eventVersion,
            String packageName,
            String recipient,
            ActionType actionType,
            String payloadHash) {
        this(eventId, eventVersion, packageName, recipient, actionType.name(), payloadHash);
    }

    public String getEventId() {
        return eventId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getActionType() {
        return actionType;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public boolean matches(ProposalBinding other) {
        return equals(other);
    }

    public boolean invalidatedBy(ProposalBinding other) {
        return !matches(other);
    }

    public static String sha256Utf8(String payload) {
        return sha256(payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload == null ? new byte[0] : payload);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProposalBinding)) {
            return false;
        }
        ProposalBinding that = (ProposalBinding) other;
        return eventVersion == that.eventVersion
                && eventId.equals(that.eventId)
                && packageName.equals(that.packageName)
                && recipient.equals(that.recipient)
                && actionType.equals(that.actionType)
                && payloadHash.equals(that.payloadHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, eventVersion, packageName, recipient, actionType, payloadHash);
    }
}
