package com.textureflow.intelligence.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.EventSignal;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TickSchedulerTest {
    private TickScheduler scheduler;

    @After
    public void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    public void newestSignalWinsWhileQueued() throws Exception {
        scheduler = new TickScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> processed = new ArrayList<>();
        scheduler.enqueue(signal("evt_1", 1), first -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            processed.add(first.getEventId());
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        scheduler.enqueue(signal("evt_2", 2), later -> processed.add(later.getEventId()));
        scheduler.enqueue(signal("evt_3", 3), later -> processed.add(later.getEventId()));
        release.countDown();
        scheduler.awaitIdle(2, TimeUnit.SECONDS);
        assertEquals(2, processed.size());
        assertEquals("evt_1", processed.get(0));
        assertEquals("evt_3", processed.get(1));
    }

    @Test
    public void coalesceKeyPrefersPersonId() {
        assertEquals("person_sam", TickScheduler.coalesceKey(signal("evt_1", 1)));
        EventSignal unnamed = new EventSignal(EventSignal.Kind.POSTED, "evt_x", 1, "", "pkg");
        assertEquals("evt_x", TickScheduler.coalesceKey(unnamed));
    }

    private static EventSignal signal(String eventId, int version) {
        return new EventSignal(EventSignal.Kind.POSTED, eventId, version, "person_sam", "com.whatsapp");
    }
}
