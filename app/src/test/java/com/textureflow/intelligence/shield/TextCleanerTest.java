package com.textureflow.intelligence.shield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class TextCleanerTest {
    @Test
    public void cleanTextMatchesTypeScriptRules() {
        assertEquals("hello world", TextCleaner.cleanText("  hello\n\tworld  "));
        assertEquals("sa fe", TextCleaner.cleanText("sa\u0007fe"));
        assertEquals("", TextCleaner.cleanText(null));
    }

    @Test
    public void stripPhoneNumbersRemovesUsAndInternationalForms() {
        assertEquals("Call me later.", TextCleaner.stripPhoneNumbers("Call me +1 415-555-0100 later."));
        assertEquals("See you", TextCleaner.stripPhoneNumbers("See you 415.555.0199"));
    }

    @Test
    public void wrapUntrustedIsSymmetric() {
        assertEquals("<untrusted>downstairs</untrusted>", TextCleaner.wrapUntrusted("downstairs"));
        assertEquals("downstairs", TextCleaner.unwrapUntrusted("<untrusted>downstairs</untrusted>"));
    }

    @Test
    public void truncateNeverSplitsSurrogatePairs() {
        String emoji = "xx\uD83D\uDE00yy";
        String truncated = TextCleaner.truncateCodePoints(emoji, 3);
        assertEquals("xx\uD83D\uDE00", truncated);
        assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));
    }
}
