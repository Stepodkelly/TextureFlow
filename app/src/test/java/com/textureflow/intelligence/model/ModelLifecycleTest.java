package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class ModelLifecycleTest {
    @Test
    public void lazyLoadsOnFirstGenerateAndUnloadsAfterIdle() throws Exception {
        FakeModelPort fake = new FakeModelPort();
        fake.text = "{}";
        MutableModelClock clock = new MutableModelClock(1_000L);
        ModelLifecycle life = new ModelLifecycle(fake, clock, DeviceGuard.NEVER_BLOCKED);

        assertFalse(life.isLoaded());
        life.generate(new ModelRequest("hello", 8, 0.2f));
        assertEquals(1, fake.loads);
        assertEquals(1, fake.generates);
        assertTrue(life.isLoaded());

        clock.now = 1_000L + ModelLifecycle.IDLE_UNLOAD_MS - 1;
        assertFalse(life.unloadIfIdle(clock.now));
        assertTrue(life.isLoaded());

        clock.now = 1_000L + ModelLifecycle.IDLE_UNLOAD_MS;
        assertTrue(life.unloadIfIdle(clock.now));
        assertFalse(life.isLoaded());
        assertEquals(1, fake.unloads);
    }

    @Test
    public void onTrimMemoryUnloadsImmediately() throws Exception {
        FakeModelPort fake = new FakeModelPort();
        fake.text = "{}";
        ModelLifecycle life = new ModelLifecycle(fake);
        life.generate(new ModelRequest("hello", 8, 0.2f));
        life.onTrimMemory();
        assertFalse(life.isLoaded());
        assertEquals(1, fake.unloads);
    }

    @Test
    public void thermalOrBatteryTreatsPortAsUnavailable() {
        FakeModelPort fake = new FakeModelPort();
        ModelLifecycle life = new ModelLifecycle(fake, ModelClock.SYSTEM, () -> true);
        assertTrue(life.isThermalOrBatteryBlocked());
        assertFalse(life.isAvailable());
        try {
            life.generate(new ModelRequest("hello", 8, 0.2f));
            fail("blocked generate must throw");
        } catch (Exception e) {
            assertTrue(e instanceof ModelUnavailableException);
        }
        assertEquals(0, fake.loads);
        assertEquals(0, fake.generates);
    }

    @Test
    public void noModelPortIsUnavailable() {
        ModelLifecycle life = new ModelLifecycle(new NoModelPort());
        assertFalse(life.isAvailable());
    }
}
