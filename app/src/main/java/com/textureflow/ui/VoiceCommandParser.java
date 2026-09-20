package com.textureflow.ui;

/** Pure parsing helpers for bounded local voice commands. */
final class VoiceCommandParser {
    private VoiceCommandParser() {}

    static String replyDraft(String spoken, String normalized) {
        String[] trailingCommands = {
                ", reply with that", " reply with that", ", send that", " send that",
                ", tell them that", " tell them that"
        };
        for (String command : trailingCommands) {
            int index = normalized.lastIndexOf(command);
            if (index > 0 && index + command.length() == normalized.length()) {
                String draft = spoken.substring(0, index).trim();
                while (draft.endsWith(",") || draft.endsWith(".") || draft.endsWith(";")) {
                    draft = draft.substring(0, draft.length() - 1).trim();
                }
                return draft.isEmpty() ? null : draft;
            }
        }
        String[] prefixes = {
                "reply with ", "reply saying ", "respond with ",
                "respond saying ", "reply ", "say ", "tell them "
        };
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix) && spoken.length() > prefix.length()) {
                String draft = spoken.substring(prefix.length()).trim();
                return draft.isEmpty() ? null : draft;
            }
        }
        return null;
    }
}
