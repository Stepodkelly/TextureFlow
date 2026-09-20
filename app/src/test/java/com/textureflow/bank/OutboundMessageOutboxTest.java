package com.textureflow.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OutboundMessageOutboxTest {
    private OutboundMessageOutbox outbox;

    @Before
    public void setUp() {
        outbox = new OutboundMessageOutbox(new MemoryPreferences());
    }

    @Test
    public void enqueuePeekMarkSent() {
        String id = outbox.enqueue("Alex", "com.whatsapp", "hello");
        List<OutboundMessage> ready = outbox.peekReady(10);
        assertEquals(1, ready.size());
        assertEquals(id, ready.get(0).id());
        assertEquals("hello", ready.get(0).body());
        outbox.markSent(id);
        assertEquals(0, outbox.size());
    }

    @Test
    public void markFailedIncrementsAttempts() {
        String id = outbox.enqueue("Alex", "com.whatsapp", "retry me");
        outbox.markFailed(id, "no handle");
        OutboundMessage message = outbox.peekReady(1).get(0);
        assertEquals(1, message.attempts());
        assertEquals("no handle", message.lastError());
        assertEquals(1, outbox.size());
    }

    @Test
    public void clearForKeyRemovesOnlyMatching() {
        outbox.enqueue("Alex", "com.whatsapp", "a");
        outbox.enqueue("Sam", "com.whatsapp", "b");
        outbox.clearForKey(new BankKey("Alex", "com.whatsapp"));
        List<OutboundMessage> ready = outbox.peekReady(10);
        assertEquals(1, ready.size());
        assertEquals("Sam", ready.get(0).peerKey());
    }

    @Test
    public void queueCapsAtMaxSizeDroppingOldest() {
        for (int i = 0; i < OutboundMessageOutbox.MAX_QUEUE_SIZE + 5; i++) {
            outbox.enqueue("Alex", "com.whatsapp", "m" + i);
        }
        assertEquals(OutboundMessageOutbox.MAX_QUEUE_SIZE, outbox.size());
        assertTrue(outbox.peekReady(1).get(0).body().startsWith("m5"));
        assertFalse(outbox.hasPendingFor(new BankKey("Nobody", "com.whatsapp")));
        assertTrue(outbox.hasPendingFor(new BankKey("Alex", "com.whatsapp")));
    }

    /** Minimal SharedPreferences for JVM unit tests without Robolectric. */
    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, String> values = new HashMap<>();

        @Override public Map<String, ?> getAll() { return values; }
        @Override public String getString(String key, String defValue) {
            return values.containsKey(key) ? values.get(key) : defValue;
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public boolean getBoolean(String key, boolean defValue) { return defValue; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        private final class MemoryEditor implements Editor {
            private final Map<String, String> pending = new HashMap<>();
            private boolean clear;

            @Override public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor putStringSet(String key, Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { return this; }
            @Override public Editor putLong(String key, long value) { return this; }
            @Override public Editor putFloat(String key, float value) { return this; }
            @Override public Editor putBoolean(String key, boolean value) { return this; }
            @Override public Editor remove(String key) {
                pending.put(key, null);
                return this;
            }
            @Override public Editor clear() {
                clear = true;
                return this;
            }
            @Override public boolean commit() {
                if (clear) values.clear();
                for (Map.Entry<String, String> e : pending.entrySet()) {
                    if (e.getValue() == null) values.remove(e.getKey());
                    else values.put(e.getKey(), e.getValue());
                }
                return true;
            }
            @Override public void apply() { commit(); }
        }
    }
}
