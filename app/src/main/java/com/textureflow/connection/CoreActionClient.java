package com.textureflow.connection;

import com.textureflow.actions.ActionType;
import com.textureflow.data.StoredNotificationEvent;

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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Phone-side USER transport for Core-owned proposal, confirmation, and receipt state. */
public final class CoreActionClient {
    public record Proposal(
            String proposalId,
            String sessionId,
            int revision,
            ActionType actionType,
            String spokenPreview,
            String replyMessage) {}

    public record Confirmation(String commandId, Receipt receipt) {}

    public record Receipt(String receiptId, String status, String message) {}

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 12_000;
    private static final int RECEIPT_POLL_MS = 650;
    private static final int MAX_RESPONSE_CHARS = 1_000_000;

    private final String convexUrl;
    private final String ownerId;
    private final String userActionToken;

    public CoreActionClient(ConnectionConfig config, String userActionToken) {
        convexUrl = config.convexUrl();
        ownerId = config.ownerId();
        this.userActionToken = required(userActionToken, "A User action token is required");
    }

    public Proposal create(
            StoredNotificationEvent event, ActionType actionType, Map<String, Object> payload)
            throws Exception {
        String nonce = UUID.randomUUID().toString();
        String proposalId = "mobile_proposal_" + nonce;
        String sessionId = "mobile_session_" + nonce;
        String preview = preview(event, actionType, payload);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("proposalId", proposalId);
        args.put("sessionId", sessionId);
        args.put("eventId", event.getEventId());
        args.put("actionType", actionType.name());
        args.put("payload", payload);
        args.put("spokenPreview", preview);
        args.put("expiresAt", expiry());
        args.put("traceId", "mobile_trace_" + nonce);
        JSONObject result = object(invoke("mutation", "proposals:create", args), "proposal result");
        JSONObject proposal = result.getJSONObject("proposal");
        return proposal(proposal, replyMessage(payload));
    }

    public Proposal reviseReply(
            Proposal current, StoredNotificationEvent event, String message) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", required(message, "Reply text is required"));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("proposalId", current.proposalId());
        args.put("expectedRevision", current.revision());
        args.put("payload", payload);
        args.put("spokenPreview", preview(event, ActionType.REPLY, payload));
        args.put("expiresAt", expiry());
        JSONObject result = object(invoke("mutation", "proposals:revise", args), "revision result");
        requireOk(result);
        return proposal(result.getJSONObject("proposal"), message.trim());
    }

    public Confirmation confirm(Proposal proposal) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("proposalId", proposal.proposalId());
        args.put("sessionId", proposal.sessionId());
        args.put("expectedRevision", proposal.revision());
        JSONObject result = object(invoke("mutation", "proposals:confirm", args), "confirmation result");
        requireOk(result);
        String commandId = result.getJSONObject("command").getString("commandId");
        Receipt receipt = result.isNull("receipt") ? null : receipt(result.getJSONObject("receipt"));
        return new Confirmation(commandId, receipt);
    }

    public void cancel(Proposal proposal) throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", actor());
        args.put("proposalId", proposal.proposalId());
        invoke("mutation", "proposals:cancel", args);
    }

    public Receipt awaitReceipt(String commandId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        do {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("actor", actor());
            args.put("commandId", commandId);
            Object value = invoke("query", "receipts:getByCommand", args);
            if (value instanceof JSONObject json) return receipt(json);
            if (System.currentTimeMillis() >= deadline) return null;
            Thread.sleep(RECEIPT_POLL_MS);
        } while (!Thread.currentThread().isInterrupted());
        return null;
    }

    private Map<String, Object> actor() {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("ownerId", ownerId);
        actor.put("role", "USER");
        actor.put("token", userActionToken);
        return actor;
    }

    private Object invoke(String endpoint, String path, Map<String, Object> args) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(convexUrl + "/api/" + endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Convex-Client", "android-textureflow-actions-0.1");

            JSONObject request = new JSONObject();
            request.put("path", path);
            request.put("format", "convex_encoded_json");
            request.put("args", new JSONArray().put(new JSONObject(args)));
            byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            JSONObject response = new JSONObject(readBounded(stream));
            if ("success".equals(response.optString("status"))) {
                Object value = response.opt("value");
                return value == JSONObject.NULL ? null : value;
            }
            throw new IOException(path + " failed");
        } catch (JSONException invalidResponse) {
            throw new IOException(path + " returned invalid data", invalidResponse);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Proposal proposal(JSONObject value, String fallbackReply) throws JSONException {
        String reply = value.optString("actionType").equals("REPLY")
                ? value.optJSONObject("payload").optString("message", fallbackReply) : "";
        return new Proposal(
                value.getString("proposalId"), value.getString("sessionId"),
                value.getInt("revision"), ActionType.valueOf(value.getString("actionType")),
                value.getString("spokenPreview"), reply);
    }

    private static Receipt receipt(JSONObject value) throws JSONException {
        return new Receipt(
                value.getString("receiptId"), value.getString("status"),
                value.optString("message", "Android returned an action receipt."));
    }

    private static String preview(
            StoredNotificationEvent event, ActionType actionType, Map<String, Object> payload) {
        String sender = value(event.getSenderName(), "this person");
        String app = value(event.getAppLabel(), "the source app");
        return switch (actionType) {
            case REPLY -> "Reply to " + sender + " on " + app + ": \""
                    + required(String.valueOf(payload.get("message")), "Reply text is required") + "\".";
            case SNOOZE -> "Snooze " + sender + "'s notification on " + app + " for one hour.";
            case DISMISS -> "Dismiss " + sender + "'s notification on " + app + ".";
        };
    }

    private static String replyMessage(Map<String, Object> payload) {
        Object value = payload.get("message");
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String expiry() {
        return Instant.now().plus(2, ChronoUnit.MINUTES).toString();
    }

    private static void requireOk(JSONObject result) throws IOException {
        if (!result.optBoolean("ok", false)) throw new IOException("Core rejected the action");
    }

    private static JSONObject object(Object value, String label) throws IOException {
        if (value instanceof JSONObject object) return object;
        throw new IOException("Core returned an invalid " + label);
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label);
        return value.trim();
    }

    private static String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Core returned no response");
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (body.length() + read > MAX_RESPONSE_CHARS) {
                    throw new IOException("Core response was too large");
                }
                body.append(buffer, 0, read);
            }
        }
        return body.toString();
    }
}
