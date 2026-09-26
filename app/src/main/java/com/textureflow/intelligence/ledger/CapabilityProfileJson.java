package com.textureflow.intelligence.ledger;

import com.textureflow.intelligence.api.CapabilityProfile;
import com.textureflow.intelligence.api.CapabilityTier;
import com.textureflow.intelligence.api.HelperCapTier;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stable JSON for the singleton {@code capability_profile} row. No Android. */
final class CapabilityProfileJson {
    private CapabilityProfileJson() {}

    static String encode(CapabilityProfile profile) {
        try {
            JSONObject object = new JSONObject();
            CapabilityProfile.Device device = profile.getDevice();
            object.put("device", new JSONObject()
                    .put("model", device.getModel())
                    .put("soc", device.getSoc())
                    .put("sdk", device.getSdk())
                    .put("ramTotalMb", device.getRamTotalMb()));
            CapabilityProfile.Runtime runtime = profile.getRuntime();
            object.put("runtime", new JSONObject()
                    .put("port", runtime.getPort())
                    .put("model", runtime.getModel())
                    .put("backend", runtime.getBackend()));
            CapabilityProfile.Bench bench = profile.getBench();
            object.put("bench", new JSONObject()
                    .put("decodeTokS", bench.getDecodeTokS())
                    .put("prefill600Ms", bench.getPrefill600Ms())
                    .put("triageWallMs", bench.getTriageWallMs())
                    .put("benchmarkedAt", bench.getBenchmarkedAt()));
            CapabilityProfile.Budget budget = profile.getBudget();
            object.put("budget", new JSONObject()
                    .put("ramAvailableMb", budget.getRamAvailableMb())
                    .put("parallelRoles", budget.getParallelRoles())
                    .put("thermal", budget.getThermal()));
            object.put("tier", profile.getTier().name());
            object.put("helperCapTier", profile.getHelperCapTier().name());
            JSONArray roles = new JSONArray();
            for (String role : profile.getRolesEligible()) {
                roles.put(role);
            }
            object.put("rolesEligible", roles);
            JSONArray shards = new JSONArray();
            for (CapabilityProfile.ShardSpan shard : profile.getShardEligible()) {
                shards.put(new JSONObject()
                        .put("model", shard.getModel())
                        .put("fromLayer", shard.getFromLayer())
                        .put("toLayer", shard.getToLayer()));
            }
            object.put("shardEligible", shards);
            object.put("maxParallelCouncilSlots", profile.getMaxParallelCouncilSlots());
            object.put("maxParallelDepthJobs", profile.getMaxParallelDepthJobs());
            object.put("network", new JSONObject()
                    .put("rttMsToPrimary", profile.getNetwork().getRttMsToPrimary())
                    .put("throughputMbps", profile.getNetwork().getThroughputMbps()));
            object.put("power", new JSONObject()
                    .put("charging", profile.getPower().isCharging())
                    .put("batteryPct", profile.getPower().getBatteryPct()));
            return object.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("encode capability profile", error);
        }
    }

    static CapabilityProfile decode(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(json);
            JSONObject device = object.getJSONObject("device");
            JSONObject runtime = object.getJSONObject("runtime");
            JSONObject bench = object.getJSONObject("bench");
            JSONObject budget = object.getJSONObject("budget");
            JSONObject network = object.optJSONObject("network");
            JSONObject power = object.optJSONObject("power");
            return new CapabilityProfile(
                    new CapabilityProfile.Device(
                            device.getString("model"),
                            device.getString("soc"),
                            device.getInt("sdk"),
                            device.getInt("ramTotalMb")),
                    new CapabilityProfile.Runtime(
                            runtime.getString("port"),
                            runtime.getString("model"),
                            runtime.getString("backend")),
                    new CapabilityProfile.Bench(
                            bench.getDouble("decodeTokS"),
                            bench.getLong("prefill600Ms"),
                            bench.getLong("triageWallMs"),
                            bench.getLong("benchmarkedAt")),
                    new CapabilityProfile.Budget(
                            budget.getInt("ramAvailableMb"),
                            budget.getInt("parallelRoles"),
                            budget.getString("thermal")),
                    CapabilityTier.valueOf(object.getString("tier")),
                    HelperCapTier.valueOf(object.optString("helperCapTier", HelperCapTier.C0.name())),
                    strings(object.optJSONArray("rolesEligible")),
                    shards(object.optJSONArray("shardEligible")),
                    object.getInt("maxParallelCouncilSlots"),
                    object.getInt("maxParallelDepthJobs"),
                    network == null
                            ? CapabilityProfile.Network.UNKNOWN
                            : new CapabilityProfile.Network(
                                    network.getInt("rttMsToPrimary"),
                                    network.getDouble("throughputMbps")),
                    power == null
                            ? CapabilityProfile.Power.UNKNOWN
                            : new CapabilityProfile.Power(
                                    power.getBoolean("charging"),
                                    power.getInt("batteryPct")));
        } catch (JSONException | IllegalArgumentException error) {
            throw new IllegalStateException("decode capability profile", error);
        }
    }

    private static List<String> strings(JSONArray array) throws JSONException {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            values.add(array.getString(i));
        }
        return values;
    }

    private static List<CapabilityProfile.ShardSpan> shards(JSONArray array) throws JSONException {
        List<CapabilityProfile.ShardSpan> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject shard = array.getJSONObject(i);
            values.add(new CapabilityProfile.ShardSpan(
                    shard.getString("model"),
                    shard.getInt("fromLayer"),
                    shard.getInt("toLayer")));
        }
        return values;
    }
}
