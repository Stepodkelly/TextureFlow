package com.textureflow.connection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ConnectionStateMachineTest {
    @Test
    public void capturesLifecycleFailureAndRecovery() {
        ConnectionStateMachine machine = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.STOPPED, machine.snapshot().state());
        machine.start(10);
        machine.registering(20);
        machine.failure(30, "offline");
        assertEquals(ConnectionStateMachine.State.BACKING_OFF, machine.snapshot().state());
        assertEquals(1, machine.snapshot().consecutiveFailures());
        machine.watchdogRecovery(40, "stalled");
        assertEquals(1, machine.snapshot().watchdogRecoveries());
        machine.online(50);
        assertEquals(ConnectionStateMachine.State.ONLINE, machine.snapshot().state());
        assertEquals(0, machine.snapshot().consecutiveFailures());
        machine.beginStop(60);
        machine.stopped(70);
        assertEquals(ConnectionStateMachine.State.STOPPED, machine.snapshot().state());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsOnlineTransitionWhileStopped() {
        new ConnectionStateMachine().online(1);
    }
}
