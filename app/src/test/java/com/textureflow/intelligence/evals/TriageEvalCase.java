package com.textureflow.intelligence.evals;

import com.textureflow.intelligence.api.AttentionLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class TriageEvalCase {
    final String id;
    final String app;
    final String sender;
    final String relationship;
    final String body;
    final long ageMinutes;
    final AttentionLevel expectedLevel;
    final boolean expectedRequiresResponse;
    final List<String> tags;
    final String packageName;
    final double personImportance;

    private TriageEvalCase(
            String id,
            String app,
            String sender,
            String relationship,
            String body,
            long ageMinutes,
            AttentionLevel expectedLevel,
            boolean expectedRequiresResponse,
            List<String> tags,
            String packageName,
            double personImportance) {
        this.id = id;
        this.app = app;
        this.sender = sender;
        this.relationship = relationship;
        this.body = body;
        this.ageMinutes = ageMinutes;
        this.expectedLevel = expectedLevel;
        this.expectedRequiresResponse = expectedRequiresResponse;
        this.tags = tags;
        this.packageName = packageName;
        this.personImportance = personImportance;
    }

    static TriageEvalCase fromJson(JSONObject json) throws JSONException {
        List<String> tags = new ArrayList<>();
        JSONArray rawTags = json.optJSONArray("tags");
        if (rawTags != null) {
            for (int i = 0; i < rawTags.length(); i++) {
                tags.add(rawTags.getString(i));
            }
        }
        String app = json.getString("app");
        String relationship = json.getString("relationship");
        return new TriageEvalCase(
                json.getString("id"),
                app,
                json.getString("sender"),
                relationship,
                json.getString("body"),
                json.getLong("ageMinutes"),
                AttentionLevel.valueOf(json.getString("expectedLevel").toUpperCase(Locale.ROOT)),
                json.getBoolean("expectedRequiresResponse"),
                Collections.unmodifiableList(tags),
                json.has("packageName") ? json.getString("packageName") : inferPackage(app),
                json.has("personImportance") ? json.getDouble("personImportance") : inferImportance(relationship));
    }

    boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    static String inferPackage(String app) {
        String key = app == null ? "" : app.toLowerCase(Locale.ROOT);
        if (key.contains("whatsapp")) {
            return "com.whatsapp";
        }
        if (key.contains("telegram")) {
            return "org.telegram.messenger";
        }
        if (key.contains("message") || key.contains("sms")) {
            return "com.google.android.apps.messaging";
        }
        if (key.contains("gmail") || key.contains("mail")) {
            return "com.google.android.gm";
        }
        if (key.contains("slack")) {
            return "com.slack";
        }
        if (key.contains("teams")) {
            return "com.microsoft.teams";
        }
        if (key.contains("amazon")) {
            return "com.amazon.mshop.android.shopping";
        }
        if (key.contains("shop")) {
            return "com.shop.app";
        }
        return "com.unknown.app";
    }

    static double inferImportance(String relationship) {
        String key = relationship == null ? "" : relationship.toLowerCase(Locale.ROOT);
        if (key.contains("partner") || key.contains("spouse")) {
            return 0.95;
        }
        if (key.contains("family")) {
            return 0.9;
        }
        if (key.contains("friend")) {
            return 0.7;
        }
        if (key.contains("coworker") || key.contains("colleague")) {
            return 0.6;
        }
        if (key.contains("group") || key.contains("acquaintance")) {
            return 0.5;
        }
        if (key.contains("brand") || key.contains("service") || key.contains("store")) {
            return 0.2;
        }
        return 0.4;
    }
}
