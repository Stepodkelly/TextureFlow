package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import java.util.Collections;

/** Default capability snapshots. Stream H replaces these after probing. */
public final class CapabilityProfiles {
    private CapabilityProfiles() {}

    public static CapabilityProfile deterministicOnly() {
        return new CapabilityProfile(
                new CapabilityProfile.Device("unknown", "", 0, 0),
                new CapabilityProfile.Runtime("none", "", "none"),
                new CapabilityProfile.Bench(0, 0, 0, 0),
                new CapabilityProfile.Budget(0, 1, "nominal"),
                CapabilityTier.T0,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                null,
                null);
    }

    public static CapabilityProfile onDevice(CapabilityTier tier) {
        return new CapabilityProfile(
                new CapabilityProfile.Device("unknown", "", 0, 0),
                new CapabilityProfile.Runtime("on_device", "", "cpu"),
                new CapabilityProfile.Bench(0, 0, 0, 0),
                new CapabilityProfile.Budget(2_048, 1, "nominal"),
                tier,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                1,
                0,
                null,
                null);
    }

    public static CapabilityProfile thermalSevere(CapabilityTier tier) {
        return new CapabilityProfile(
                new CapabilityProfile.Device("unknown", "", 0, 0),
                new CapabilityProfile.Runtime("on_device", "", "cpu"),
                new CapabilityProfile.Bench(0, 0, 0, 0),
                new CapabilityProfile.Budget(2_048, 1, "SEVERE"),
                tier,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                1,
                0,
                null,
                null);
    }
}
