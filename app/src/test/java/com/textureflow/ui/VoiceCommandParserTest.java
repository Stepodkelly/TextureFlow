package com.textureflow.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class VoiceCommandParserTest {
    @Test
    public void extractsNaturalTrailingReplyInstruction() {
        assertEquals("I am going to eat",
                VoiceCommandParser.replyDraft(
                        "I am going to eat, reply with that",
                        "i am going to eat, reply with that"));
    }

    @Test
    public void extractsDirectReplyInstruction() {
        assertEquals("I am going to eat",
                VoiceCommandParser.replyDraft(
                        "Reply with I am going to eat",
                        "reply with i am going to eat"));
    }

    @Test
    public void ignoresOrdinaryConversation() {
        assertNull(VoiceCommandParser.replyDraft("What did Stephen say?",
                "what did stephen say"));
    }
}
