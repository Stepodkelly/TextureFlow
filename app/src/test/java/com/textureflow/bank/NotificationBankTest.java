package com.textureflow.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.textureflow.actions.NotificationControl;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class NotificationBankTest {
    private NotificationBank bank;
    private RecordingControl control;

    @Before
    public void setUp() {
        bank = new NotificationBank(
                new BankStore(new MemoryPreferences()),
                new OutboundMessageOutbox(new MemoryPreferences()),
                new HandshakeRefill(new MemoryPreferences()));
        control = new RecordingControl();
    }

    @Test
    public void adoptAndArmPersistsArmedState() {
        BankEntry entry = sample("evt-1", "nkey");
        assertTrue(bank.adoptAndArm(control, entry));
        BankEntry stored = bank.peek(entry.key());
        assertNotNull(stored);
        assertEquals(BankState.ARMED_SNOOZED, stored.state());
        assertEquals(BankArming.DEFAULT_ARM_SNOOZE_MS, control.lastDurationMs);
        assertTrue(stored.snoozeUntilMillis() > 0L);
    }

    @Test
    public void requestWakeMovesToWaking() {
        BankEntry entry = sample("evt-2", "nkey-2");
        bank.adoptAndArm(control, entry);
        assertTrue(bank.requestWake(control, entry.key()));
        assertEquals(BankState.WAKING, bank.peek(entry.key()).state());
        assertEquals(BankArming.WAKE_MS, control.lastDurationMs);
    }

    @Test
    public void requestWakeFailureDoesNotStickInWaking() {
        BankEntry entry = sample("evt-2b", "nkey-2b");
        bank.adoptAndArm(control, entry);
        control.failNext = true;
        assertFalse(bank.requestWake(control, entry.key()));
        assertEquals(BankState.ARMED_SNOOZED, bank.peek(entry.key()).state());
    }

    @Test
    public void adoptAfterWakeSkipsReArmAndGoesLive() {
        BankEntry entry = sample("evt-2c", "nkey-2c");
        bank.adoptAndArm(control, entry);
        assertTrue(bank.requestWake(control, entry.key()));
        control.lastDurationMs = -1L;
        BankEntry live = bank.adoptAfterWake(sample("evt-2c", "nkey-2c-live"));
        assertEquals(BankState.LIVE, live.state());
        assertEquals(-1L, control.lastDurationMs);
        assertEquals(BankState.LIVE, bank.peek(entry.key()).state());
    }

    @Test
    public void onLiveHandleDrainsWhenOutboxPending() {
        BankEntry entry = sample("evt-3", "nkey-3");
        bank.adopt(entry);
        bank.enqueueOutbound("Alex", "com.whatsapp", "queued");
        BankEntry live = bank.onLiveHandle("evt-3");
        assertEquals(BankState.DRAINING_OUTBOX, live.state());
    }

    @Test
    public void markSpentAndRequestRefill() {
        BankEntry entry = sample("evt-4", "nkey-4");
        bank.adopt(entry);
        bank.markSpent(entry.key());
        assertEquals(BankState.SPENT, bank.peek(entry.key()).state());
        bank.requestRefill(entry.key());
        assertTrue(bank.isRefillPending(entry.key()));
        assertEquals(BankState.REFILL_PENDING, bank.peek(entry.key()).state());
    }

    @Test
    public void clearRemovesEntryAndRefill() {
        BankEntry entry = sample("evt-5", "nkey-5");
        bank.adopt(entry);
        bank.requestRefill(entry.key());
        bank.clear(entry.key());
        assertNull(bank.peek(entry.key()));
        assertFalse(bank.isRefillPending(entry.key()));
    }

    private static BankEntry sample(String eventId, String notificationKey) {
        return new BankEntry(
                new BankKey("Alex", "com.whatsapp"),
                eventId,
                notificationKey,
                "Alex",
                null,
                "hi",
                System.currentTimeMillis());
    }

    private static final class RecordingControl implements NotificationControl {
        long lastDurationMs;
        boolean failNext;

        @Override public boolean isAvailable() { return true; }
        @Override public void dismiss(String notificationKey) {}
        @Override public void snooze(String notificationKey, long durationMs) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("snooze failed");
            }
            lastDurationMs = durationMs;
        }
    }

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
            private boolean clearAll;

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
                clearAll = true;
                return this;
            }
            @Override public boolean commit() {
                if (clearAll) values.clear();
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
