package com.textureflow.intelligence.policy;

import com.textureflow.intelligence.api.AttentionLevel;
import com.textureflow.intelligence.api.IntelligenceIntent;
import com.textureflow.intelligence.api.ReplyTone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict JSON parse for council outputs. Unknown fields, missing keys, and out-of-range
 * values fail closed.
 */
public final class SchemaValidator {
    public JSONObject validate(SchemaName schema, String json) {
        if (schema == null) {
            throw new SchemaValidationException("Schema is required.");
        }
        JSONObject object = parseObject(json, schema.wireName() + " result");
        return validate(schema, object);
    }

    public JSONObject validate(SchemaName schema, JSONObject object) {
        if (schema == null) {
            throw new SchemaValidationException("Schema is required.");
        }
        if (object == null) {
            throw new SchemaValidationException(schema.wireName() + " result must be an object.");
        }
        switch (schema) {
            case TRIAGE:
                return validateTriage(object);
            case SUMMARIZER:
                return validateSummarizer(object);
            case DRAFTER:
                return validateDrafter(object);
            case SKEPTIC:
                return validateSkeptic(object);
            case SUMMARY_V1:
                return validateSummaryV1(object);
            case DRAFT_V1:
                return validateDraftV1(object);
            default:
                throw new SchemaValidationException("Unknown schema: " + schema);
        }
    }

    private JSONObject validateTriage(JSONObject object) {
        exactRecord(object, keys("intent", "requiresResponse", "urgency", "reason", "modelConfidence"), "triage result");
        enumValue(field(object, "intent"), IntelligenceIntent.values(), "intent");
        booleanValue(field(object, "requiresResponse"), "requiresResponse");
        enumValue(field(object, "urgency"), AttentionLevel.values(), "urgency");
        boundedString(field(object, "reason"), "reason", 120);
        boundedNumber(field(object, "modelConfidence"), "modelConfidence");
        return object;
    }

    private JSONObject validateSummarizer(JSONObject object) {
        exactRecord(object, keys("summary", "mentions", "ambiguities"), "summarizer result");
        boundedString(field(object, "summary"), "summary", 240);
        boundedStrings(field(object, "mentions"), "mentions", 8, 80);
        boundedStrings(field(object, "ambiguities"), "ambiguities", 3, 120);
        return object;
    }

    private JSONObject validateDrafter(JSONObject object) {
        exactRecord(object, keys("replyText", "tone"), "drafter result");
        boundedString(field(object, "replyText"), "replyText", 280);
        enumValue(field(object, "tone"), ReplyTone.values(), "tone");
        return object;
    }

    private JSONObject validateSkeptic(JSONObject object) {
        exactRecord(object, keys("ok", "issues"), "skeptic result");
        booleanValue(field(object, "ok"), "ok");
        issueList(field(object, "issues"));
        return object;
    }

    private JSONObject validateSummaryV1(JSONObject object) {
        exactRecord(object, keys(
                "summary",
                "priorityScore",
                "priorityLevel",
                "priorityReason",
                "intent",
                "requiresResponse",
                "ambiguities"), "summary result");
        boundedString(field(object, "summary"), "summary", 280);
        boundedNumber(field(object, "priorityScore"), "priorityScore");
        enumValue(field(object, "priorityLevel"), AttentionLevel.values(), "priorityLevel");
        boundedString(field(object, "priorityReason"), "priorityReason", 220);
        enumValue(field(object, "intent"), IntelligenceIntent.values(), "intent");
        booleanValue(field(object, "requiresResponse"), "requiresResponse");
        boundedStrings(field(object, "ambiguities"), "ambiguities", 3, 120);
        return object;
    }

    private JSONObject validateDraftV1(JSONObject object) {
        exactRecord(object, keys("text", "tone", "confidence", "ambiguities"), "draft reply result");
        boundedString(field(object, "text"), "text", 500);
        enumValue(field(object, "tone"), ReplyTone.values(), "tone");
        boundedNumber(field(object, "confidence"), "confidence");
        boundedStrings(field(object, "ambiguities"), "ambiguities", 3, 120);
        return object;
    }

    private static JSONObject parseObject(String json, String label) {
        if (json == null || json.trim().isEmpty()) {
            throw new SchemaValidationException(label + " must be an object.");
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{")) {
            throw new SchemaValidationException(label + " must be an object.");
        }
        try {
            return new JSONObject(trimmed);
        } catch (JSONException e) {
            throw new SchemaValidationException(label + " is not valid JSON.", e);
        }
    }

    private static Object field(JSONObject object, String key) {
        return object.opt(key);
    }

    private static void exactRecord(JSONObject object, Set<String> keys, String label) {
        List<String> unknown = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!keys.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new SchemaValidationException(label + " contains unknown keys: " + String.join(", ", unknown) + ".");
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new SchemaValidationException(label + " is missing keys: " + String.join(", ", missing) + ".");
        }
    }

    private static String boundedString(Object value, String field, int maximum) {
        if (!(value instanceof String)) {
            throw new SchemaValidationException(field + " must be a non-empty string of at most " + maximum + " characters.");
        }
        String text = ((String) value).trim();
        if (text.isEmpty() || ((String) value).length() > maximum) {
            throw new SchemaValidationException(field + " must be a non-empty string of at most " + maximum + " characters.");
        }
        return text;
    }

    private static double boundedNumber(Object value, String field) {
        if (!(value instanceof Number) || value instanceof Boolean) {
            throw new SchemaValidationException(field + " must be a finite number from 0 to 1.");
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < 0.0d || number > 1.0d) {
            throw new SchemaValidationException(field + " must be a finite number from 0 to 1.");
        }
        return number;
    }

    private static boolean booleanValue(Object value, String field) {
        if (!(value instanceof Boolean)) {
            throw new SchemaValidationException(field + " must be a boolean.");
        }
        return (Boolean) value;
    }

    private static <T extends Enum<T>> T enumValue(Object value, T[] allowed, String field) {
        if (!(value instanceof String)) {
            throw new SchemaValidationException(field + " has an unsupported value.");
        }
        String raw = (String) value;
        for (T candidate : allowed) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new SchemaValidationException(field + " has an unsupported value.");
    }

    private static List<String> boundedStrings(Object value, String field, int maximumItems, int maximumLength) {
        if (!(value instanceof JSONArray)) {
            throw new SchemaValidationException(field + " must contain at most " + maximumItems + " strings.");
        }
        JSONArray array = (JSONArray) value;
        if (array.length() > maximumItems) {
            throw new SchemaValidationException(field + " must contain at most " + maximumItems + " strings.");
        }
        List<String> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item;
            try {
                item = array.get(i);
            } catch (JSONException e) {
                throw new SchemaValidationException(field + "[" + i + "] is invalid.", e);
            }
            items.add(boundedString(item, field + "[" + i + "]", maximumLength));
        }
        return items;
    }

    private static List<SkepticIssue> issueList(Object value) {
        if (!(value instanceof JSONArray)) {
            throw new SchemaValidationException("issues must contain only known skeptic issue names.");
        }
        JSONArray array = (JSONArray) value;
        LinkedHashSet<SkepticIssue> issues = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Object item;
            try {
                item = array.get(i);
            } catch (JSONException e) {
                throw new SchemaValidationException("issues[" + i + "] is invalid.", e);
            }
            if (!(item instanceof String)) {
                throw new SchemaValidationException("issues has an unsupported value.");
            }
            issues.add(SkepticIssue.fromWire((String) item));
        }
        return new ArrayList<>(issues);
    }

    private static Set<String> keys(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }
}
