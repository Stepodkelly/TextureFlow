package com.textureflow.connection;

import com.textureflow.actions.ActionType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal Convex HTTP client using only Android/Java platform APIs. */
public final class ConvexHttpGateway implements RemoteGateway {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    private final ConnectionConfig config;

    public ConvexHttpGateway(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public void registerDevice() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("deviceId", config.deviceId());
        args.put("displayName", config.deviceDisplayName());
        args.put("platform", "ANDROID");
        args.put("status", "ONLINE");
        args.put("appVersion", config.appVersion());
        mutate("devices:register", args);
    }

    @Override
    public void heartbeat() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("appVersion", config.appVersion());
        args.put("deviceTimestamp", Instant.now().toString());
        mutate("devices:heartbeat", args);
    }

    @Override
    public void uploadEvent(String eventJson, String traceId) throws Exception {
        JSONObject event = new JSONObject(eventJson);
        if ("REMOVED".equals(event.optString("status"))) {
            uploadRemovedEvent(eventJson, traceId);
            return;
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("event", toJavaObject(event));
        args.put("traceId", traceId);
        try {
            mutate("events:upsert", args);
        } catch (IOException failure) {
            if (!serverCoversEvent(event)) throw failure;
        }
    }

    @Override
    public void uploadRemovedEvent(String eventJson, String traceId) throws Exception {
        JSONObject event = new JSONObject(eventJson);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("eventId", requiredString(event, "eventId"));
        args.put("version", event.getInt("version"));
        args.put("updatedAt", requiredString(event, "updatedAt"));
        args.put("traceId", traceId);
        try {
            mutate("events:markRemoved", args);
        } catch (IOException failure) {
            if (!serverCoversEvent(event)) throw failure;
        }
    }

    @Override
    public void uploadReceipt(String receiptJson, ClaimRecord claim) throws Exception {
        JSONObject receipt = new JSONObject(receiptJson);
        if (!claim.commandId().equals(requiredString(receipt, "commandId"))) {
            throw new SecurityException("Receipt command does not match its persisted claim");
        }
        // Core binds receipts to the original command trace, not the executor's local diagnostic trace.
        receipt.put("traceId", claim.traceId());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("claimToken", claim.claimToken());
        args.put("receipt", toJavaObject(receipt));
        mutate("receipts:complete", args);
    }

    @Override
    public List<RemoteCommand> pollCommands(int limit) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("limit", Math.max(1, Math.min(50, limit)));
        Object value = query("commands:forDevice", args);
        if (!(value instanceof JSONArray array)) {
            throw new IOException("Convex command query returned a non-array value");
        }
        List<RemoteCommand> commands = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            commands.add(parseCommand(array.getJSONObject(index)));
        }
        return commands;
    }

    @Override
    public ClaimResult claimCommand(String commandId, String claimToken) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("commandId", commandId);
        args.put("claimToken", claimToken);
        Object value = mutate("commands:claim", args);
        JSONObject result = requireObject(value, "claim result");
        boolean claimed = result.getBoolean("claimed");
        String reason = optionalString(result, "reason");
        RemoteCommand command = result.isNull("command")
                ? null : parseCommand(result.getJSONObject("command"));
        return new ClaimResult(claimed, reason, command);
    }

    @Override
    public RemoteProposal loadProposal(String proposalId) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("proposalId", proposalId);
        return parseProposal(requireObject(query("proposals:get", args), "proposal"));
    }

    @Override
    public StartResult startExecution(String commandId, String claimToken) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("commandId", commandId);
        args.put("claimToken", claimToken);
        JSONObject result = requireObject(mutate("commands:startExecution", args), "start result");
        String operation = requiredString(result, "operation");
        RemoteCommand command = result.isNull("command")
                ? null : parseCommand(result.getJSONObject("command"));
        boolean executable = "UPDATE".equals(operation) || "IDEMPOTENT".equals(operation);
        return new StartResult(executable, operation, command);
    }

    private Map<String, Object> actor() {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("ownerId", config.ownerId());
        actor.put("role", "DEVICE");
        actor.put("deviceId", config.deviceId());
        if (config.deviceActorToken() != null && !config.deviceActorToken().trim().isEmpty()) {
            actor.put("token", config.deviceActorToken());
        }
        return actor;
    }

    private Object query(String path, Map<String, Object> args) throws Exception {
        return invoke("query", path, args);
    }

    private Object mutate(String path, Map<String, Object> args) throws Exception {
        return invoke("mutation", path, args);
    }

    private boolean serverCoversEvent(JSONObject localEvent) {
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("actor", actor());
            args.put("eventId", requiredString(localEvent, "eventId"));
            JSONObject server = requireObject(query("events:get", args), "event");
            int serverVersion = server.getInt("version");
            int localVersion = localEvent.getInt("version");
            if (serverVersion > localVersion) return true;
            if (serverVersion < localVersion) return false;
            String[] fields = {
                    "contractVersion", "eventId", "deviceId", "app", "sender",
                    "conversationLabel", "body", "postedAt", "updatedAt", "version",
                    "status", "priority"
            };
            for (String field : fields) {
                if (!jsonEquivalent(server.opt(field), localEvent.opt(field))) return false;
            }
            if ("REMOVED".equals(localEvent.optString("status"))) return true;
            return stringSet(server.optJSONArray("capabilities"))
                    .equals(stringSet(localEvent.optJSONArray("capabilities")));
        } catch (Exception unavailableOrMissing) {
            return false;
        }
    }

    private static boolean jsonEquivalent(Object left, Object right) throws JSONException {
        if (left == JSONObject.NULL) left = null;
        if (right == JSONObject.NULL) right = null;
        if (left == null || right == null) return left == right;
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        if (left instanceof JSONObject leftObject && right instanceof JSONObject rightObject) {
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (java.util.Iterator<String> iterator = leftObject.keys(); iterator.hasNext();) {
                keys.add(iterator.next());
            }
            for (java.util.Iterator<String> iterator = rightObject.keys(); iterator.hasNext();) {
                keys.add(iterator.next());
            }
            for (String key : keys) {
                if (!jsonEquivalent(leftObject.opt(key), rightObject.opt(key))) return false;
            }
            return true;
        }
        if (left instanceof JSONArray leftArray && right instanceof JSONArray rightArray) {
            if (leftArray.length() != rightArray.length()) return false;
            for (int index = 0; index < leftArray.length(); index++) {
                if (!jsonEquivalent(leftArray.opt(index), rightArray.opt(index))) return false;
            }
            return true;
        }
        return left.equals(right);
    }

    private static java.util.Set<String> stringSet(JSONArray array) throws JSONException {
        java.util.Set<String> values = new java.util.HashSet<>();
        if (array == null) return values;
        for (int index = 0; index < array.length(); index++) values.add(array.getString(index));
        return values;
    }

    private Object invoke(String endpoint, String path, Map<String, Object> args) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(config.convexUrl() + "/api/" + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Convex-Client", "android-textureflow-0.1");
            if (config.oidcToken() != null && !config.oidcToken().trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + config.oidcToken());
            }

            JSONObject request = new JSONObject();
            request.put("path", path);
            request.put("format", "convex_encoded_json");
            request.put("args", new JSONArray().put(toJsonObject(args)));
            byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBounded(stream);
            JSONObject response = new JSONObject(body);
            if ("success".equals(response.optString("status"))) {
                Object value = response.opt("value");
                return value == JSONObject.NULL ? null : value;
            }
            String message = response.optString("errorMessage", "Convex function failed");
            throw new IOException(path + " failed: " + truncate(message, 400));
        } catch (JSONException invalidResponse) {
            throw new IOException(path + " returned invalid JSON", invalidResponse);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static RemoteCommand parseCommand(JSONObject json) throws JSONException {
        return new RemoteCommand(
                json.getInt("contractVersion"),
                requiredString(json, "commandId"),
                requiredString(json, "ownerId"),
                requiredString(json, "proposalId"),
                requiredString(json, "targetDeviceId"),
                requiredString(json, "eventId"),
                json.getInt("expectedEventVersion"),
                ActionType.fromWire(requiredString(json, "actionType")),
                toJavaMap(json.getJSONObject("payload")),
                requiredString(json, "idempotencyKey"),
                requiredString(json, "status"),
                requiredString(json, "sourceMode"),
                requiredString(json, "traceId"),
                requiredString(json, "createdAt"),
                requiredString(json, "expiresAt"));
    }

    private static RemoteProposal parseProposal(JSONObject json) throws JSONException {
        String confirmedAt = optionalString(json, "confirmedAt");
        if (confirmedAt == null && json.has("confirmedAtMs") && !json.isNull("confirmedAtMs")) {
            confirmedAt = Instant.ofEpochMilli(json.getLong("confirmedAtMs")).toString();
        }
        if (confirmedAt == null) throw new JSONException("Confirmed proposal has no confirmation time");
        return new RemoteProposal(
                requiredString(json, "proposalId"),
                requiredString(json, "ownerId"),
                requiredString(json, "targetDeviceId"),
                requiredString(json, "eventId"),
                json.getInt("expectedEventVersion"),
                ActionType.fromWire(requiredString(json, "actionType")),
                toJavaMap(json.getJSONObject("payload")),
                requiredString(json, "status"),
                confirmedAt,
                requiredString(json, "expiresAt"));
    }

    private static JSONObject toJsonObject(Map<String, Object> values) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            object.put(entry.getKey(), toJsonValue(entry.getValue()));
        }
        return object;
    }

    private static Object toJsonValue(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Map<?, ?> map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.put(String.valueOf(entry.getKey()), toJsonValue(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Iterable<?> iterable) {
            JSONArray array = new JSONArray();
            for (Object item : iterable) array.put(toJsonValue(item));
            return array;
        }
        return value;
    }

    private static Object toJavaObject(Object value) throws JSONException {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof JSONObject object) return toJavaMap(object);
        if (value instanceof JSONArray array) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                values.add(toJavaObject(array.get(index)));
            }
            return values;
        }
        return value;
    }

    private static Map<String, Object> toJavaMap(JSONObject object) throws JSONException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (java.util.Iterator<String> keys = object.keys(); keys.hasNext();) {
            String key = keys.next();
            Object value = toJavaObject(object.get(key));
            if (value != null) values.put(key, value);
        }
        return Collections.unmodifiableMap(values);
    }

    private static JSONObject requireObject(Object value, String label) throws IOException {
        if (value instanceof JSONObject object) return object;
        throw new IOException("Convex returned an invalid " + label);
    }

    private static String requiredString(JSONObject object, String key) throws JSONException {
        String value = object.getString(key);
        if (value.trim().isEmpty()) throw new JSONException(key + " is blank");
        return value;
    }

    private static String optionalString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Convex returned no response body");
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4_096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (result.length() + read > MAX_RESPONSE_CHARS) {
                    throw new IOException("Convex response exceeded the size limit");
                }
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
