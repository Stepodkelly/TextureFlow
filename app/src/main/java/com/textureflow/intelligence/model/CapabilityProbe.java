package com.textureflow.intelligence.model;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds {@link CapabilityProfile} from device facts plus optional bench numbers.
 * Part II shard / helper lists stay empty.
 */
public final class CapabilityProbe {
    /** Devices with under 2 GB total RAM stay T0 even if a file is present. */
    public static final int T0_RAM_AVAILABLE_MB = 2048;

    private CapabilityProbe() {}

    public static CapabilityProfile probe(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        CapabilityTier tier = tier(snapshot);
        CapabilityProfile.Runtime runtime = runtimeFor(snapshot);
        CapabilityProfile.Bench bench = snapshot.bench == null
                ? new CapabilityProfile.Bench(0, 0, 0, 0)
                : snapshot.bench;
        String thermal = snapshot.thermal == null || snapshot.thermal.isEmpty()
                ? "nominal"
                : snapshot.thermal;
        return new CapabilityProfile(
                new CapabilityProfile.Device(snapshot.model, snapshot.soc, snapshot.sdk, snapshot.ramTotalMb),
                runtime,
                bench,
                new CapabilityProfile.Budget(snapshot.ramAvailableMb, 1, thermal),
                tier,
                HelperCapTier.C0,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                CapabilityProfile.Network.UNKNOWN,
                new CapabilityProfile.Power(snapshot.charging, snapshot.batteryPct));
    }

    public static CapabilityProfile fromAndroid(Context context) {
        return fromAndroid(context, null);
    }

    public static CapabilityProfile fromAndroid(Context context, CapabilityProfile.Bench bench) {
        return probe(Snapshot.fromAndroid(context, bench));
    }

    public static CapabilityTier tier(Snapshot snapshot) {
        if (snapshot.ramTotalMb > 0 && snapshot.ramTotalMb < T0_RAM_AVAILABLE_MB) {
            return CapabilityTier.T0;
        }
        if (!snapshot.modelFilePresent && !snapshot.gpuOrPlatform) {
            return CapabilityTier.T0;
        }
        if (snapshot.gpuOrPlatform) {
            return CapabilityTier.T3;
        }
        if (snapshot.xiaomiClass()) {
            return CapabilityTier.T1;
        }
        Double tok = snapshot.decodeTokS();
        if (tok != null) {
            if (tok > 15.0d) {
                return CapabilityTier.T3;
            }
            if (tok >= 5.0d) {
                return CapabilityTier.T2;
            }
            return CapabilityTier.T1;
        }
        return CapabilityTier.T1;
    }

    private static CapabilityProfile.Runtime runtimeFor(Snapshot snapshot) {
        if (snapshot.gpuOrPlatform) {
            return new CapabilityProfile.Runtime("aicore", snapshot.runtimeModel, "platform");
        }
        if (snapshot.modelFilePresent) {
            String port = snapshot.runtimePort == null || snapshot.runtimePort.isEmpty()
                    ? "mediapipe_tasks_genai"
                    : snapshot.runtimePort;
            String model = snapshot.runtimeModel == null || snapshot.runtimeModel.isEmpty()
                    ? "gemma3-1b-it-int4"
                    : snapshot.runtimeModel;
            String backend = snapshot.runtimeBackend == null || snapshot.runtimeBackend.isEmpty()
                    ? "cpu"
                    : snapshot.runtimeBackend;
            return new CapabilityProfile.Runtime(port, model, backend);
        }
        return new CapabilityProfile.Runtime("none", "", "none");
    }

    public static final class Snapshot {
        public final String manufacturer;
        public final String model;
        public final String soc;
        public final int sdk;
        public final int ramTotalMb;
        public final int ramAvailableMb;
        public final boolean modelFilePresent;
        public final boolean gpuOrPlatform;
        public final boolean charging;
        public final int batteryPct;
        public final String thermal;
        public final CapabilityProfile.Bench bench;
        public final String runtimePort;
        public final String runtimeModel;
        public final String runtimeBackend;

        public Snapshot(
                String manufacturer,
                String model,
                String soc,
                int sdk,
                int ramTotalMb,
                int ramAvailableMb,
                boolean modelFilePresent,
                boolean gpuOrPlatform,
                boolean charging,
                int batteryPct,
                String thermal,
                CapabilityProfile.Bench bench,
                String runtimePort,
                String runtimeModel,
                String runtimeBackend) {
            this.manufacturer = manufacturer == null ? "" : manufacturer;
            this.model = model == null ? "" : model;
            this.soc = soc == null ? "" : soc;
            this.sdk = sdk;
            this.ramTotalMb = ramTotalMb;
            this.ramAvailableMb = ramAvailableMb;
            this.modelFilePresent = modelFilePresent;
            this.gpuOrPlatform = gpuOrPlatform;
            this.charging = charging;
            this.batteryPct = batteryPct;
            this.thermal = thermal;
            this.bench = bench;
            this.runtimePort = runtimePort;
            this.runtimeModel = runtimeModel;
            this.runtimeBackend = runtimeBackend;
        }

        public boolean xiaomiClass() {
            String mfr = manufacturer.toLowerCase(Locale.ROOT);
            String modelKey = model.toLowerCase(Locale.ROOT);
            String socKey = soc.toLowerCase(Locale.ROOT);
            if (mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco")) {
                return true;
            }
            if (modelKey.contains("m2101k6g") || modelKey.contains("redmi")) {
                return true;
            }
            return socKey.equals("sm7150") || socKey.contains("sm7150");
        }

        public Double decodeTokS() {
            if (bench == null || bench.getDecodeTokS() <= 0) {
                return null;
            }
            return bench.getDecodeTokS();
        }

        public static Snapshot fromAndroid(Context context, CapabilityProfile.Bench bench) {
            Objects.requireNonNull(context, "context");
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                manager.getMemoryInfo(memory);
            }
            int ramTotal = (int) (memory.totalMem / (1024 * 1024));
            int ramAvail = (int) (memory.availMem / (1024 * 1024));
            File resolved = ModelFiles.resolve(context);
            boolean present = ModelFiles.isPresent(resolved);
            boolean platform = PlatformModelProbe.aicorePresent(context);
            BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int pct = battery != null
                    ? battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    : 0;
            boolean charging = battery != null
                    && battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                    == BatteryManager.BATTERY_STATUS_CHARGING;
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            String thermal = "nominal";
            if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                thermal = thermalLabel(power.getCurrentThermalStatus());
            }
            String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.SOC_MODEL : "";
            return new Snapshot(
                    Build.MANUFACTURER,
                    Build.MODEL,
                    soc,
                    Build.VERSION.SDK_INT,
                    ramTotal,
                    ramAvail,
                    present,
                    platform,
                    charging,
                    pct,
                    thermal,
                    bench,
                    present ? "mediapipe_tasks_genai" : "none",
                    present ? "gemma3-1b-it-int4" : "",
                    platform ? "platform" : present ? "cpu" : "none");
        }

        private static String thermalLabel(int status) {
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                return "severe";
            }
            if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                return "moderate";
            }
            return "nominal";
        }
    }
}
