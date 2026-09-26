package com.textureflow.intelligence.roles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the user's intended reply out of a spoken instruction (doc §6.3).
 * "tell Sam I'm coming" → "I'm coming".
 */
final class LiteralUserWords {
    private static final Pattern TELL_PRONOUN = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:tell|say|text|message)\\s+(him|her|them)(?:\\s+that)?\\s+(.+)$");
    private static final Pattern TELL_NAMED = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:tell|text|message)\\s+(?:to\\s+)?(\\S+)(?:\\s+that)?\\s+(.+)$");
    private static final Pattern REPLY_TO = Pattern.compile(
            "(?i)^(?:please\\s+)?reply\\s+to\\s+(\\S+)(?:\\s+that)?\\s+(.+)$");
    private static final Pattern SAY = Pattern.compile(
            "(?i)^(?:please\\s+)?say(?:\\s+that)?\\s+(.+)$");
    private static final Pattern ASSISTANT_INSTRUCTION = Pattern.compile(
            "(?i)^(?:please\\s+)?(tell|say|text|message|reply|write|draft|make|rewrite|polish|clean)\\b");

    private LiteralUserWords() {}

    static String resolve(String userRequest, String userDraftText) {
        String draft = userDraftText == null ? "" : userDraftText.trim();
        if (!draft.isEmpty()) {
            return draft;
        }
        return extractFromSpoken(userRequest == null ? "" : userRequest.trim());
    }

    static String extractFromSpoken(String request) {
        if (request == null || request.isEmpty()) {
            return "";
        }
        Matcher pronoun = TELL_PRONOUN.matcher(request);
        if (pronoun.matches()) {
            return pronoun.group(2).trim();
        }
        Matcher reply = REPLY_TO.matcher(request);
        if (reply.matches()) {
            return reply.group(2).trim();
        }
        Matcher named = TELL_NAMED.matcher(request);
        if (named.matches()) {
            return named.group(2).trim();
        }
        Matcher say = SAY.matcher(request);
        if (say.matches()) {
            return say.group(1).trim();
        }
        return request;
    }

    static boolean isCompleteReply(String words) {
        if (words == null || words.isEmpty()) {
            return false;
        }
        return !looksLikeAssistantInstruction(words);
    }

    static boolean looksLikeAssistantInstruction(String words) {
        return ASSISTANT_INSTRUCTION.matcher(words.trim()).find();
    }

    static String instructionForGate(String userRequest, String userDraftText) {
        String request = userRequest == null ? "" : userRequest.trim();
        String draft = userDraftText == null ? "" : userDraftText.trim();
        if (request.isEmpty()) {
            return draft;
        }
        if (draft.isEmpty() || draft.equals(request)) {
            return request;
        }
        return request + " " + draft;
    }
}
