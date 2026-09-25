package com.textureflow.intelligence.shield;

import java.util.regex.Pattern;

/** Ports {@code cleanText} from {@code intelligence/src/context.ts} and redacts identifiers. */
public final class TextCleaner {
    static final String UNTRUSTED_OPEN = "<untrusted>";
    static final String UNTRUSTED_CLOSE = "</untrusted>";

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /**
     * Phone-like runs. Conservative enough to drop numbers the model must not see;
     * short codes stay for {@link com.textureflow.intelligence.policy.NoInventedSecretsRule}.
     */
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?:\\+|\\b)(?:\\d[\\s().-]*){7,15}\\d\\b");

    private TextCleaner() {}

    public static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String withoutControls = CONTROL_CHARS.matcher(value).replaceAll(" ");
        return WHITESPACE.matcher(withoutControls).replaceAll(" ").trim();
    }

    public static String stripPhoneNumbers(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return WHITESPACE.matcher(PHONE_NUMBER.matcher(value).replaceAll(" ")).replaceAll(" ").trim();
    }

    public static String wrapUntrusted(String body) {
        return UNTRUSTED_OPEN + (body == null ? "" : body) + UNTRUSTED_CLOSE;
    }

    public static String unwrapUntrusted(String wrapped) {
        if (wrapped == null) {
            return "";
        }
        if (wrapped.startsWith(UNTRUSTED_OPEN) && wrapped.endsWith(UNTRUSTED_CLOSE)) {
            return wrapped.substring(
                    UNTRUSTED_OPEN.length(),
                    wrapped.length() - UNTRUSTED_CLOSE.length());
        }
        return wrapped;
    }

    public static int codePointLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    public static String substringCodePoints(String value, int startInclusive, int endExclusive) {
        if (value == null || value.isEmpty() || endExclusive <= startInclusive) {
            return "";
        }
        int start = value.offsetByCodePoints(0, startInclusive);
        int end = value.offsetByCodePoints(0, endExclusive);
        return value.substring(start, end);
    }

    /** Same ellipsis rule as {@code context.ts}, but never splits a surrogate pair. */
    public static String truncateCodePoints(String value, int maximum) {
        if (value == null) {
            return "";
        }
        int length = codePointLength(value);
        if (length <= maximum) {
            return value;
        }
        if (maximum <= 3) {
            return substringCodePoints(value, 0, maximum);
        }
        String head = substringCodePoints(value, 0, maximum - 3);
        return head.replaceAll("\\s+$", "") + "...";
    }
}
