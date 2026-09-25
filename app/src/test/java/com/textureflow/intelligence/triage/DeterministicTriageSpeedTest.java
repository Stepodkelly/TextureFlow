package com.textureflow.intelligence.triage;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DeterministicTriageSpeedTest {
    @Test
    public void oneThousandAssessmentsFinishUnder200Ms() {
        IdentityResolver resolver = new IdentityResolver();
        TriageEvent event = TriageEvent.atMillis(
                "speed",
                "com.whatsapp",
                "I'm downstairs. The door is locked. Can you let me in?",
                1_775_719_800_000L,
                "Sam K",
                null);
        IdentityResolution identity = resolver.resolveEvent(event);
        long now = 1_775_719_920_000L;

        for (int warm = 0; warm < 200; warm++) {
            DeterministicTriage.assess(event, identity, now);
        }

        long started = System.nanoTime();
        for (int index = 0; index < 1_000; index++) {
            DeterministicTriage.assess(event, identity, now);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertTrue("1000 assessments took " + elapsedMs + " ms", elapsedMs < 200);
    }
}
