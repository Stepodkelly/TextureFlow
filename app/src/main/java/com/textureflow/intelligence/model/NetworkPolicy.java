package com.textureflow.intelligence.model;

/** Download gate. Production policy is Wi‑Fi only. */
public interface NetworkPolicy {
    boolean allowDownload();

    NetworkPolicy ALWAYS = () -> true;
    NetworkPolicy NEVER = () -> false;
}
