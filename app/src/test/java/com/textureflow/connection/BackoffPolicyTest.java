package com.textureflow.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackoffPolicyTest {
    @Test
    public void growsExponentiallyAndCaps() {
        BackoffPolicy policy = new BackoffPolicy(1_000L, 8_000L, 0.20);
        assertEquals(1_000L, policy.delayMillis(1, 0.5));
        assertEquals(2_000L, policy.delayMillis(2, 0.5));
        assertEquals(8_000L, policy.delayMillis(10, 0.5));
    }

    @Test
    public void jitterStaysInsideConfiguredBounds() {
        BackoffPolicy policy = new BackoffPolicy(1_000L, 8_000L, 0.20);
        assertEquals(800L, policy.delayMillis(1, 0.0));
        assertEquals(1_200L, policy.delayMillis(1, 1.0));
        assertTrue(policy.delayMillis(40, 1.0) <= 8_000L);
    }
}
