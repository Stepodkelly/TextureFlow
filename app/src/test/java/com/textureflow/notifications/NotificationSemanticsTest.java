package com.textureflow.notifications;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class NotificationSemanticsTest {
    @Test
    public void fingerprintsAreDeterministicAndOpaque() {
        String rawKey = "0|com.example.chat|42|private-notification-key";
        String first = ContentFingerprint.eventId("device-a", "com.example.chat", rawKey);
        String second = ContentFingerprint.eventId("device-a", "com.example.chat", rawKey);
        assertEquals(first, second);
        assertTrue(first.startsWith("evt_"));
        assertFalse(first.contains(rawKey));
        assertNotEquals(first, ContentFingerprint.eventId("device-b", "com.example.chat", rawKey));
    }

    @Test
    public void eventVersionsChangeOnlyForMeaningfulStateChanges() {
        assertEquals(1, EventVersionPolicy.nextVersion(0, null, null, "hash-a"));
        assertEquals("ACTIVE", EventVersionPolicy.nextStatus(0, null, null, "hash-a"));
        assertEquals(4, EventVersionPolicy.nextVersion(4, "UPDATED", "hash-a", "hash-a"));
        assertEquals(5, EventVersionPolicy.nextVersion(4, "ACTIVE", "hash-a", "hash-b"));
        assertEquals("UPDATED", EventVersionPolicy.nextStatus(4, "ACTIVE", "hash-a", "hash-b"));
        assertEquals(6, EventVersionPolicy.nextVersion(5, "REMOVED", "hash-b", "hash-b"));
    }
}
