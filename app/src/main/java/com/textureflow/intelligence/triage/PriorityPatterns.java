package com.textureflow.intelligence.triage;

import java.util.regex.Pattern;

/**
 * The five case-insensitive regexes from {@code intelligence/src/priority.ts},
 * plus the two helper patterns used only inside scoring.
 */
public final class PriorityPatterns {
    public static final Pattern PROMOTION = Pattern.compile(
            "\\b(?:sale|discount|coupon|promo(?:tion)?|limited offer|% off|shop now|unsubscribe|deal ends|free shipping)\\b",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern MALICIOUS_INSTRUCTION = Pattern.compile(
            "(?:ignore (?:all |any )?(?:previous|prior|system)|system prompt|developer message|call (?:the )?(?:tool|function)|confirm[_ ]action|execute (?:the )?command|mark (?:it )?as dispatched|reveal (?:the )?(?:secret|api key))",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern URGENT = Pattern.compile(
            "\\b(?:urgent|asap|emergency|immediately|right now|locked out|door is locked|downstairs|waiting outside|hospital|help me|deadline today|due today)\\b",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern STRONG_REQUEST = Pattern.compile(
            "\\b(?:can you|could you|would you|please|need you to|will you|are we|what time|when will|where are|let me know|reply|call me)\\b",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern REQUEST = Pattern.compile(
            "\\?|\\b(?:need|want|send|bring|tell|confirm|check)\\b",
            Pattern.CASE_INSENSITIVE);

    static final Pattern STRONG_URGENCY = Pattern.compile(
            "\\b(?:emergency|hospital|help me|locked out|door is locked|waiting outside)\\b",
            Pattern.CASE_INSENSITIVE);

    static final Pattern ACCESS_REQUEST = Pattern.compile(
            "\\b(?:locked|downstairs|waiting)\\b",
            Pattern.CASE_INSENSITIVE);

    static final Pattern SOURCE_DIRECT = Pattern.compile(
            "whatsapp|telegram|messag|sms",
            Pattern.CASE_INSENSITIVE);

    static final Pattern SOURCE_WORK = Pattern.compile(
            "mail|slack|teams",
            Pattern.CASE_INSENSITIVE);

    private PriorityPatterns() {}

    public static boolean containsUntrustedInstruction(String body) {
        return body != null && !body.isEmpty() && MALICIOUS_INSTRUCTION.matcher(body).find();
    }

    public static boolean isPromotional(String body) {
        return body != null && !body.isEmpty() && PROMOTION.matcher(body).find();
    }
}
