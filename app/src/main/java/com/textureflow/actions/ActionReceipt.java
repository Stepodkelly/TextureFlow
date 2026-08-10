package com.textureflow.actions;

import org.json.JSONException;
import org.json.JSONObject;

public final class ActionReceipt {
    public static final int CONTRACT_VERSION = 1;

    private final String receiptId;
    private final String commandId;
    private final String deviceId;
    private final String status;
    private final ActionErrorCode errorCode;
    private final String message;
    private final String deviceTimestamp;
    private final String textureCue;
    private final String traceId;

    public ActionReceipt(
            String receiptId,
            String commandId,
            String deviceId,
            String status,
            ActionErrorCode errorCode,
            String message,
            String deviceTimestamp,
            String textureCue,
            String traceId) {
        this.receiptId = receiptId;
        this.commandId = commandId;
        this.deviceId = deviceId;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.deviceTimestamp = deviceTimestamp;
        this.textureCue = textureCue;
        this.traceId = traceId;
    }

    public String getReceiptId() { return receiptId; }
    public String getCommandId() { return commandId; }
    public String getDeviceId() { return deviceId; }
    public String getStatus() { return status; }
    public ActionErrorCode getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getDeviceTimestamp() { return deviceTimestamp; }
    public String getTextureCue() { return textureCue; }
    public String getTraceId() { return traceId; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("contractVersion", CONTRACT_VERSION);
        json.put("receiptId", receiptId);
        json.put("commandId", commandId);
        json.put("deviceId", deviceId);
        json.put("status", status);
        if (errorCode != null) {
            json.put("errorCode", errorCode.name());
        }
        json.put("message", message);
        json.put("deviceTimestamp", deviceTimestamp);
        json.put("textureCue", textureCue);
        json.put("traceId", traceId);
        return json;
    }
}
