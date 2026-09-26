package com.textureflow.intelligence.jobs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.textureflow.intelligence.api.JobClass;

import org.junit.Test;

public final class JobClassRegistryTest {
    @Test
    public void registersBuiltInLocalJobs() {
        JobClassRegistry registry = new JobClassRegistry();
        assertTrue(registry.ids().contains(JobClassRegistry.TRIAGE));
        assertTrue(registry.ids().contains(JobClassRegistry.SUMMARY));
        assertTrue(registry.ids().contains(JobClassRegistry.DRAFT));
        for (String id : registry.ids()) {
            JobClass job = registry.require(id);
            assertFalse(id + " must not allow depth", job.allowDepth());
            assertEquals(0f, job.minEffectiveParamsB(), 0.0);
        }
    }

    @Test
    public void rejectsUnknownIds() {
        JobClassRegistry registry = new JobClassRegistry();
        assertFalse(registry.isRegistered("thread_catch_up_v1"));
        try {
            registry.require("thread_catch_up_v1");
            fail("unknown id should throw");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("thread_catch_up_v1"));
        }
    }
}
