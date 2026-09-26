package com.textureflow.intelligence.roles;

/**
 * Pulls a JSON object out of a model completion that may include fences or chatter.
 */
final class ModelJson {
    private ModelJson() {}

    static String extractObject(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String fenced = stripFence(trimmed);
        int start = fenced.indexOf('{');
        int end = fenced.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return fenced;
        }
        return fenced.substring(start, end + 1);
    }

    private static String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int newline = text.indexOf('\n');
        if (newline < 0) {
            return text;
        }
        String body = text.substring(newline + 1);
        int close = body.lastIndexOf("```");
        if (close >= 0) {
            return body.substring(0, close).trim();
        }
        return body.trim();
    }
}
