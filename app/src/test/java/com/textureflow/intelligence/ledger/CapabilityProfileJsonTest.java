package com.textureflow.intelligence.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class CapabilityProfileJsonTest {
    @Test
    public void decodeNullOrEmptyIsNull() {
        assertNull(CapabilityProfileJson.decode(null));
        assertNull(CapabilityProfileJson.decode(""));
    }

    @Test
    public void roundTripsEmptyHelperLists() {
        CapabilityProfile original = sampleProfile();
        CapabilityProfile decoded = CapabilityProfileJson.decode(CapabilityProfileJson.encode(original));
        assertEquals("Pixel 8", decoded.getDevice().getModel());
        assertEquals("Tensor", decoded.getDevice().getSoc());
        assertEquals(34, decoded.getDevice().getSdk());
        assertEquals(8000, decoded.getDevice().getRamTotalMb());
        assertEquals("none", decoded.getRuntime().getPort());
        assertEquals(CapabilityTier.T0, decoded.getTier());
        assertEquals(HelperCapTier.C0, decoded.getHelperCapTier());
        assertTrue(decoded.getRolesEligible().isEmpty());
        assertTrue(decoded.getShardEligible().isEmpty());
        assertEquals(0, decoded.getMaxParallelCouncilSlots());
        assertEquals(0, decoded.getNetwork().getRttMsToPrimary());
        assertEquals(0, decoded.getPower().getBatteryPct());
    }

    @Test
    public void roundTripsRolesAndShards() {
        CapabilityProfile original = new CapabilityProfile(
                new CapabilityProfile.Device("Pixel 8", "Tensor", 34, 8000),
                new CapabilityProfile.Runtime("mediapipe", "gemma-3-1b", "litert"),
                new CapabilityProfile.Bench(42.5, 600, 80, 1_700_000_000_000L),
                new CapabilityProfile.Budget(2000, 2, "nominal"),
                CapabilityTier.T2,
                HelperCapTier.C1,
                Arrays.asList("TRIAGE", "DRAFTER"),
                Collections.singletonList(new CapabilityProfile.ShardSpan("gemma-3-1b", 0, 8)),
                2,
                1,
                new CapabilityProfile.Network(12, 80.5),
                new CapabilityProfile.Power(true, 64));
        CapabilityProfile decoded = CapabilityProfileJson.decode(CapabilityProfileJson.encode(original));
        assertEquals(CapabilityTier.T2, decoded.getTier());
        assertEquals(HelperCapTier.C1, decoded.getHelperCapTier());
        assertEquals(Arrays.asList("TRIAGE", "DRAFTER"), decoded.getRolesEligible());
        assertEquals(1, decoded.getShardEligible().size());
        assertEquals("gemma-3-1b", decoded.getShardEligible().get(0).getModel());
        assertEquals(0, decoded.getShardEligible().get(0).getFromLayer());
        assertEquals(8, decoded.getShardEligible().get(0).getToLayer());
        assertEquals(12, decoded.getNetwork().getRttMsToPrimary());
        assertEquals(80.5, decoded.getNetwork().getThroughputMbps(), 0.0);
        assertTrue(decoded.getPower().isCharging());
        assertEquals(64, decoded.getPower().getBatteryPct());
        assertEquals("mediapipe", decoded.getRuntime().getPort());
        assertEquals(42.5, decoded.getBench().getDecodeTokS(), 0.0);
    }

    private static CapabilityProfile sampleProfile() {
        return new CapabilityProfile(
                new CapabilityProfile.Device("Pixel 8", "Tensor", 34, 8000),
                new CapabilityProfile.Runtime("none", "", "none"),
                new CapabilityProfile.Bench(0, 0, 0, 0),
                new CapabilityProfile.Budget(2000, 1, "nominal"),
                CapabilityTier.T0,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                CapabilityProfile.Network.UNKNOWN,
                CapabilityProfile.Power.UNKNOWN);
    }
}
