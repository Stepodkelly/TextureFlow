package com.textureflow.connection;

import static org.junit.Assert.assertThrows;

import com.textureflow.actions.ActionType;

import org.junit.Test;

import java.util.Map;

public final class CommandEnvelopeValidatorTest {
    @Test
    public void exactConfirmedPayloadIsAccepted() {
        CommandEnvelopeValidator.requireExactMatch(
                command(Map.of("message", "On my way")),
                proposal(Map.of("message", "On my way")),
                "owner-1",
                "device-1");
    }

    @Test
    public void revisedPayloadCannotReuseOldConfirmation() {
        assertThrows(SecurityException.class, () -> CommandEnvelopeValidator.requireExactMatch(
                command(Map.of("message", "Changed text")),
                proposal(Map.of("message", "On my way")),
                "owner-1",
                "device-1"));
    }

    @Test
    public void commandForAnotherDeviceIsRejectedBeforeClaim() {
        assertThrows(SecurityException.class, () -> CommandEnvelopeValidator.requireExecutableTarget(
                command(Map.of("message", "On my way")), "owner-1", "device-2"));
    }

    private static RemoteCommand command(Map<String, Object> payload) {
        return new RemoteCommand(
                1, "command-1", "owner-1", "proposal-1", "device-1", "event-1", 3,
                ActionType.REPLY, payload, "idem-1", "CLAIMED", "LIVE", "trace-1",
                "2026-08-09T18:00:00Z", "2026-08-09T18:01:00Z");
    }

    private static RemoteProposal proposal(Map<String, Object> payload) {
        return new RemoteProposal(
                "proposal-1", "owner-1", "device-1", "event-1", 3,
                ActionType.REPLY, payload, "COMMITTED",
                "2026-08-09T18:00:10Z", "2026-08-09T18:01:00Z");
    }
}
