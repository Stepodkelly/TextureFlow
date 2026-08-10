package com.textureflow.connection;

public interface ClaimStore {
    ClaimRecord find(String commandId);
    ClaimRecord getOrCreate(RemoteCommand command, long nowMillis);
    void remove(String commandId);
}
