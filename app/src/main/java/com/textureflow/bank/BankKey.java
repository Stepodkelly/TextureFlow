package com.textureflow.bank;

import java.util.Objects;

/** Identity for a banked replyable notification: peer × messaging package. */
public final class BankKey {
    private final String peerKey;
    private final String packageName;

    public BankKey(String peerKey, String packageName) {
        if (peerKey == null || peerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("peerKey is required");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName is required");
        }
        this.peerKey = peerKey.trim();
        this.packageName = packageName.trim();
    }

    public String peerKey() { return peerKey; }
    public String packageName() { return packageName; }

    public String storageKey() {
        return peerKey + '\u0001' + packageName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BankKey that)) return false;
        return peerKey.equals(that.peerKey) && packageName.equals(that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerKey, packageName);
    }
}
