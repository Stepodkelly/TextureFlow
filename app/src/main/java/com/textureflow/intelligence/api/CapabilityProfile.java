package com.textureflow.intelligence.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Per-install capability snapshot (doc §9.1 + empty Part II fields from §30).
 * Helper/shard lists stay empty on a phone with no paired devices.
 */
public final class CapabilityProfile {
    private final Device device;
    private final Runtime runtime;
    private final Bench bench;
    private final Budget budget;
    private final CapabilityTier tier;
    private final HelperCapTier helperCapTier;
    private final List<String> rolesEligible;
    private final List<ShardSpan> shardEligible;
    private final int maxParallelCouncilSlots;
    private final int maxParallelDepthJobs;
    private final Network network;
    private final Power power;

    public CapabilityProfile(
            Device device,
            Runtime runtime,
            Bench bench,
            Budget budget,
            CapabilityTier tier,
            HelperCapTier helperCapTier,
            List<String> rolesEligible,
            List<ShardSpan> shardEligible,
            int maxParallelCouncilSlots,
            int maxParallelDepthJobs,
            Network network,
            Power power) {
        this.device = Objects.requireNonNull(device, "device");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.bench = Objects.requireNonNull(bench, "bench");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.helperCapTier = helperCapTier == null ? HelperCapTier.C0 : helperCapTier;
        this.rolesEligible = copyStrings(rolesEligible);
        this.shardEligible = copyShards(shardEligible);
        this.maxParallelCouncilSlots = maxParallelCouncilSlots;
        this.maxParallelDepthJobs = maxParallelDepthJobs;
        this.network = network == null ? Network.UNKNOWN : network;
        this.power = power == null ? Power.UNKNOWN : power;
    }

    public Device getDevice() { return device; }
    public Runtime getRuntime() { return runtime; }
    public Bench getBench() { return bench; }
    public Budget getBudget() { return budget; }
    public CapabilityTier getTier() { return tier; }
    public HelperCapTier getHelperCapTier() { return helperCapTier; }
    public List<String> getRolesEligible() { return rolesEligible; }
    public List<ShardSpan> getShardEligible() { return shardEligible; }
    public int getMaxParallelCouncilSlots() { return maxParallelCouncilSlots; }
    public int getMaxParallelDepthJobs() { return maxParallelDepthJobs; }
    public Network getNetwork() { return network; }
    public Power getPower() { return power; }

    private static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<ShardSpan> copyShards(List<ShardSpan> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class Device {
        private final String model;
        private final String soc;
        private final int sdk;
        private final int ramTotalMb;

        public Device(String model, String soc, int sdk, int ramTotalMb) {
            this.model = model == null ? "" : model;
            this.soc = soc == null ? "" : soc;
            this.sdk = sdk;
            this.ramTotalMb = ramTotalMb;
        }

        public String getModel() { return model; }
        public String getSoc() { return soc; }
        public int getSdk() { return sdk; }
        public int getRamTotalMb() { return ramTotalMb; }
    }

    public static final class Runtime {
        private final String port;
        private final String model;
        private final String backend;

        public Runtime(String port, String model, String backend) {
            this.port = port == null ? "none" : port;
            this.model = model == null ? "" : model;
            this.backend = backend == null ? "none" : backend;
        }

        public String getPort() { return port; }
        public String getModel() { return model; }
        public String getBackend() { return backend; }
    }

    public static final class Bench {
        private final double decodeTokS;
        private final long prefill600Ms;
        private final long triageWallMs;
        private final long benchmarkedAt;

        public Bench(double decodeTokS, long prefill600Ms, long triageWallMs, long benchmarkedAt) {
            this.decodeTokS = decodeTokS;
            this.prefill600Ms = prefill600Ms;
            this.triageWallMs = triageWallMs;
            this.benchmarkedAt = benchmarkedAt;
        }

        public double getDecodeTokS() { return decodeTokS; }
        public long getPrefill600Ms() { return prefill600Ms; }
        public long getTriageWallMs() { return triageWallMs; }
        public long getBenchmarkedAt() { return benchmarkedAt; }
    }

    public static final class Budget {
        private final int ramAvailableMb;
        private final int parallelRoles;
        private final String thermal;

        public Budget(int ramAvailableMb, int parallelRoles, String thermal) {
            this.ramAvailableMb = ramAvailableMb;
            this.parallelRoles = parallelRoles;
            this.thermal = thermal == null ? "nominal" : thermal;
        }

        public int getRamAvailableMb() { return ramAvailableMb; }
        public int getParallelRoles() { return parallelRoles; }
        public String getThermal() { return thermal; }
    }

    public static final class ShardSpan {
        private final String model;
        private final int fromLayer;
        private final int toLayer;

        public ShardSpan(String model, int fromLayer, int toLayer) {
            this.model = model == null ? "" : model;
            this.fromLayer = fromLayer;
            this.toLayer = toLayer;
        }

        public String getModel() { return model; }
        public int getFromLayer() { return fromLayer; }
        public int getToLayer() { return toLayer; }
    }

    public static final class Network {
        public static final Network UNKNOWN = new Network(0, 0);

        private final int rttMsToPrimary;
        private final double throughputMbps;

        public Network(int rttMsToPrimary, double throughputMbps) {
            this.rttMsToPrimary = rttMsToPrimary;
            this.throughputMbps = throughputMbps;
        }

        public int getRttMsToPrimary() { return rttMsToPrimary; }
        public double getThroughputMbps() { return throughputMbps; }
    }

    public static final class Power {
        public static final Power UNKNOWN = new Power(false, 0);

        private final boolean charging;
        private final int batteryPct;

        public Power(boolean charging, int batteryPct) {
            this.charging = charging;
            this.batteryPct = batteryPct;
        }

        public boolean isCharging() { return charging; }
        public int getBatteryPct() { return batteryPct; }
    }
}
