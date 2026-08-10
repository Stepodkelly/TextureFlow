package com.textureflow.connection;

public interface ConnectionObserver {
    void onState(ConnectionStateMachine.Snapshot snapshot);

    ConnectionObserver NONE = snapshot -> {};
}
