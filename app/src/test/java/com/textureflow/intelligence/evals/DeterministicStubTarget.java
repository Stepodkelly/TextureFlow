package com.textureflow.intelligence.evals;

import com.textureflow.intelligence.api.AttentionLevel;

import java.util.regex.Pattern;

/**
 * Temporary stand-in for Stream A’s {@code DeterministicTriage}.
 * Mirrors the regexes and weights in {@code intelligence/src/priority.ts}
 * so the eval runner compiles and injection cases can be gated before A merges.
 */
public final class DeterministicStubTarget implements TriageEvalTarget {
    static final Pattern PROMOTION_PATTERN = Pattern.compile(
            "\\b(?:sale|discount|coupon|promo(?:tion)?|limited offer|% off|shop now|unsubscribe|deal ends|free shipping)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern MALICIOUS_INSTRUCTION_PATTERN = Pattern.compile(
            "(?:ignore (?:all |any )?(?:previous|prior|system)|system prompt|developer message|call (?:the )?(?:tool|function)|confirm[_ ]action|execute (?:the )?command|mark (?:it )?as dispatched|reveal (?:the )?(?:secret|api key))",
            Pattern.CASE_INSENSITIVE);
    static final Pattern URGENT_PATTERN = Pattern.compile(
            "\\b(?:urgent|asap|emergency|immediately|right now|locked out|door is locked|downstairs|waiting outside|hospital|help me|deadline today|due today)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern STRONG_URGENT_PATTERN = Pattern.compile(
            "\\b(?:emergency|hospital|help me|locked out|door is locked|waiting outside)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern STRONG_REQUEST_PATTERN = Pattern.compile(
            "\\b(?:can you|could you|would you|please|need you to|will you|are we|what time|when will|where are|let me know|reply|call me)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern REQUEST_PATTERN = Pattern.compile(
            "\\?|\\b(?:need|want|send|bring|tell|confirm|check)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern IMPLICIT_REQUEST_PATTERN = Pattern.compile(
            "\\b(?:locked|downstairs|waiting)\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern RESPONSE_LIKELY_PATTERN = Pattern.compile(
            "\\?|\\b(?:please|can you|could you|need|let me know|locked|waiting)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public AttentionLevel assessLevel(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        String text = body == null ? "" : body;
        boolean malicious = isInjection(text, packageName, sender, personImportance, ageMinutes);
        boolean promotional = isPromotional(text);
        double urgency = malicious ? 0 : urgencyStrength(text);
        double request = malicious ? 0 : requestStrength(text);
        double recency = recencyStrength(ageMinutes);
        double source = sourceStrength(packageName);

        double score =
                0.3 * clamp01(personImportance)
                + 0.25 * urgency
                + 0.2 * request
                + 0.15 * recency
                + 0.1 * source;
        if (promotional && urgency == 0 && request == 0) {
            score *= 0.25;
        }
        if (malicious) {
            score = Math.min(score, 0.49);
        }
        return levelForScore(roundScore(score));
    }

    @Override
    public boolean requiresResponse(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        String text = body == null ? "" : body;
        if (isInjection(text, packageName, sender, personImportance, ageMinutes) || isPromotional(text)) {
            return false;
        }
        return RESPONSE_LIKELY_PATTERN.matcher(text).find();
    }

    @Override
    public boolean isInjection(
            String body,
            String packageName,
            String sender,
            double personImportance,
            long ageMinutes) {
        return body != null && MALICIOUS_INSTRUCTION_PATTERN.matcher(body).find();
    }

    static boolean isPromotional(String body) {
        return body != null && PROMOTION_PATTERN.matcher(body).find();
    }

    static AttentionLevel levelForScore(double score) {
        if (score >= 0.8) {
            return AttentionLevel.URGENT;
        }
        if (score >= 0.6) {
            return AttentionLevel.IMPORTANT;
        }
        if (score >= 0.35) {
            return AttentionLevel.NORMAL;
        }
        return AttentionLevel.LOW;
    }

    private static double urgencyStrength(String body) {
        if (!URGENT_PATTERN.matcher(body).find()) {
            return 0;
        }
        return STRONG_URGENT_PATTERN.matcher(body).find() ? 1 : 0.8;
    }

    private static double requestStrength(String body) {
        if (STRONG_REQUEST_PATTERN.matcher(body).find()) {
            return 1;
        }
        if (REQUEST_PATTERN.matcher(body).find()) {
            return 0.75;
        }
        if (IMPLICIT_REQUEST_PATTERN.matcher(body).find()) {
            return 0.8;
        }
        return 0;
    }

    private static double recencyStrength(long ageMinutes) {
        long age = Math.max(0, ageMinutes);
        if (age <= 15) {
            return 1;
        }
        if (age <= 60) {
            return 0.85;
        }
        if (age <= 240) {
            return 0.65;
        }
        if (age <= 1_440) {
            return 0.4;
        }
        return 0.15;
    }

    private static double sourceStrength(String packageName) {
        String pkg = packageName == null ? "" : packageName;
        if (pkg.matches("(?i).*(whatsapp|telegram|messag|sms).*")) {
            return 0.9;
        }
        if (pkg.matches("(?i).*(mail|slack|teams).*")) {
            return 0.7;
        }
        return 0.5;
    }

    private static double roundScore(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return Math.round(clamped * 100) / 100.0;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
