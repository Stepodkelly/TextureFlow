package com.textureflow.intelligence.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import org.junit.Test;

public final class CapabilityProbeTest {
    @Test
    public void t0WhenNoModelFile() {
        CapabilityProbe.Snapshot snap = snapshot(false, false, false, null, 3000);
        assertEquals(CapabilityTier.T0, CapabilityProbe.tier(snap));
        CapabilityProfile profile = CapabilityProbe.probe(snap);
        assertEquals("none", profile.getRuntime().getPort());
        assertTrue(profile.getShardEligible().isEmpty());
        assertTrue(profile.getRolesEligible().isEmpty());
        assertEquals(HelperCapTier.C0, profile.getHelperCapTier());
        assertEquals(0, profile.getMaxParallelCouncilSlots());
    }

    @Test
    public void t0WhenTotalRamBelowTwoGig() {
        CapabilityProbe.Snapshot snap = new CapabilityProbe.Snapshot(
                "Unknown",
                "Tiny",
                "",
                26,
                1536,
                800,
                true,
                false,
                false,
                50,
                "nominal",
                new CapabilityProfile.Bench(8.0d, 1000, 1000, 1),
                "mediapipe_tasks_genai",
                "gemma3-1b-it-int4",
                "cpu");
        assertEquals(CapabilityTier.T0, CapabilityProbe.tier(snap));
    }

    @Test
    public void t1ForXiaomiClassEvenWithMidBench() {
        CapabilityProbe.Snapshot snap = new CapabilityProbe.Snapshot(
                "Xiaomi",
                "M2101K6G",
                "SM7150",
                33,
                5569,
                1924,
                true,
                false,
                false,
                80,
                "nominal",
                new CapabilityProfile.Bench(8.0d, 10_533L, 10_533L, 1L),
                "mediapipe_tasks_genai",
                "gemma3-1b-it-int4",
                "cpu");
        assertTrue(snap.xiaomiClass());
        assertEquals(CapabilityTier.T1, CapabilityProbe.tier(snap));
        CapabilityProfile profile = CapabilityProbe.probe(snap);
        assertEquals("mediapipe_tasks_genai", profile.getRuntime().getPort());
        assertEquals("gemma3-1b-it-int4", profile.getRuntime().getModel());
    }

    @Test
    public void t1WhenSlowDecode() {
        assertEquals(CapabilityTier.T1, CapabilityProbe.tier(snapshot(true, false, false, 4.9d, 4000)));
    }

    @Test
    public void t2WhenMidDecodeOnNonXiaomi() {
        assertEquals(CapabilityTier.T2, CapabilityProbe.tier(snapshot(true, false, false, 8.0d, 4000)));
        assertEquals(CapabilityTier.T2, CapabilityProbe.tier(snapshot(true, false, false, 15.0d, 4000)));
    }

    @Test
    public void t3WhenGpuOrPlatformOrFastDecode() {
        assertEquals(CapabilityTier.T3, CapabilityProbe.tier(snapshot(false, true, false, null, 4000)));
        assertEquals(CapabilityTier.T3, CapabilityProbe.tier(snapshot(true, false, false, 16.0d, 4000)));
    }

    private static CapabilityProbe.Snapshot snapshot(
            boolean filePresent,
            boolean gpuOrPlatform,
            boolean xiaomi,
            Double decodeTokS,
            int ramAvailableMb) {
        CapabilityProfile.Bench bench = decodeTokS == null
                ? null
                : new CapabilityProfile.Bench(decodeTokS, 1000, 1000, 1);
        return new CapabilityProbe.Snapshot(
                xiaomi ? "Xiaomi" : "Google",
                xiaomi ? "M2101K6G" : "Pixel 8",
                xiaomi ? "SM7150" : "Tensor",
                34,
                xiaomi ? 5569 : 8192,
                ramAvailableMb,
                filePresent,
                gpuOrPlatform,
                true,
                70,
                "nominal",
                bench,
                filePresent ? "mediapipe_tasks_genai" : "none",
                filePresent ? "gemma3-1b-it-int4" : "",
                gpuOrPlatform ? "platform" : "cpu");
    }
}
