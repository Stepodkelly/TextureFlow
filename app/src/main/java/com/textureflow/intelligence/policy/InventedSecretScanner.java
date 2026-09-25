package com.textureflow.intelligence.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds URLs, phone numbers, and codes that a draft must not invent. */
final class InventedSecretScanner {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>]+");
    private static final Pattern PHONE = Pattern.compile("(?:\\+|\\b)(?:\\d[\\s().-]*){7,15}\\d\\b");
    private static final Pattern CODE = Pattern.compile(
            "\\b(?:sk-[A-Za-z0-9]+|[A-Z]{2,}\\d{2,}[A-Z0-9]*|\\d{4,8})\\b");

    private InventedSecretScanner() {}

    static List<String> secretsNotInInstruction(String draftText, String userInstruction) {
        String draft = draftText == null ? "" : draftText;
        String instruction = userInstruction == null ? "" : userInstruction;
        String normalizedInstruction = normalize(instruction);
        List<String> invented = new ArrayList<>();
        collect(URL.matcher(draft), normalizedInstruction, invented, false);
        collect(PHONE.matcher(draft), normalize(instruction.replaceAll("[\\s().-]", "")), invented, true);
        collect(CODE.matcher(draft), normalizedInstruction, invented, false);
        return invented;
    }

    private static void collect(
            Matcher matcher,
            String normalizedInstruction,
            List<String> invented,
            boolean digitsOnly) {
        while (matcher.find()) {
            String raw = matcher.group();
            String needle = digitsOnly ? raw.replaceAll("[\\s().-]", "") : normalize(raw);
            if (needle.isEmpty()) {
                continue;
            }
            if (!normalizedInstruction.contains(needle)) {
                invented.add(raw);
            }
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
