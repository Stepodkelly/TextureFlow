package com.textureflow.policy;

import com.textureflow.actions.ActionType;
import com.textureflow.actions.ConfirmedProposal;

import java.time.Instant;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class CommandSemanticsTest {
    @Test
    public void proposalEvidenceRequiresConfirmedState() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConfirmedProposal(
                    "proposal-1", "owner-1", "device-1", "event-1", 2, ActionType.REPLY,
                    Collections.singletonMap("message", "On my way"), "PROPOSED",
                    "2026-08-09T18:10:00Z", "2026-08-09T18:11:00Z"));

        ConfirmedProposal confirmed = new ConfirmedProposal(
                "proposal-1", "owner-1", "device-1", "event-1", 2, ActionType.REPLY,
                Collections.singletonMap("message", "On my way"), "CONFIRMED",
                "2026-08-09T18:10:00Z", "2026-08-09T18:11:00Z");
        assertEquals("On my way", confirmed.stringPayload("message"));
    }

    @Test
    public void actionTypesAreStrict() {
        assertEquals(ActionType.REPLY, ActionType.fromWire("reply"));
        assertThrows(IllegalArgumentException.class, () -> ActionType.fromWire("SEND_NOW"));
    }

    @Test
    public void expiryIsFailClosed() {
        long now = Instant.parse("2026-08-09T18:10:00Z").toEpochMilli();
        assertFalse(CommandFreshness.isExpired("2026-08-09T18:11:00Z", now));
        assertTrue(CommandFreshness.isExpired("2026-08-09T18:10:00Z", now));
        assertTrue(CommandFreshness.isExpired("not-a-time", now));
        assertTrue(CommandFreshness.isExpired(null, now));
    }
}
