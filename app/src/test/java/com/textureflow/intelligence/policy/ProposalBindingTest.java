package com.textureflow.intelligence.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.textureflow.actions.ActionType;

import org.junit.Test;

public final class ProposalBindingTest {
    @Test
    public void sha256IsStableForTheSamePayload() {
        assertEquals(
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                ProposalBinding.sha256Utf8("test"));
        assertEquals(ProposalBinding.sha256Utf8("hello"), ProposalBinding.sha256Utf8("hello"));
        assertFalse(ProposalBinding.sha256Utf8("hello").equals(ProposalBinding.sha256Utf8("Hello")));
    }

    @Test
    public void everyFieldChangeInvalidatesTheBinding() {
        String hash = ProposalBinding.sha256Utf8("I'm coming down.");
        ProposalBinding base = new ProposalBinding(
                "event-1", 4, "com.whatsapp", "Sam", ActionType.REPLY, hash);

        assertTrue(base.matches(new ProposalBinding(
                "event-1", 4, "com.whatsapp", "Sam", "REPLY", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-2", 4, "com.whatsapp", "Sam", "REPLY", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-1", 5, "com.whatsapp", "Sam", "REPLY", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-1", 4, "org.telegram.messenger", "Sam", "REPLY", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-1", 4, "com.whatsapp", "Maya", "REPLY", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-1", 4, "com.whatsapp", "Sam", "DISMISS", hash)));
        assertTrue(base.invalidatedBy(new ProposalBinding(
                "event-1", 4, "com.whatsapp", "Sam", "REPLY", ProposalBinding.sha256Utf8("changed"))));
    }
}
