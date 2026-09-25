package com.textureflow.intelligence.shield;

import java.util.regex.Pattern;

/**
 * Ports {@code MALICIOUS_INSTRUCTION_PATTERN} from {@code priority.ts} and adds T01/T13 strings
 * that the TypeScript regex does not cover.
 */
public final class InjectionDetector {
    /** Exact port of {@code intelligence/src/priority.ts} {@code MALICIOUS_INSTRUCTION_PATTERN}. */
    static final Pattern TS_MALICIOUS_INSTRUCTION = Pattern.compile(
            "(?:ignore (?:all |any )?(?:previous|prior|system)|system prompt|developer message"
                    + "|call (?:the )?(?:tool|function)|confirm[_ ]action|execute (?:the )?command"
                    + "|mark (?:it )?as dispatched|reveal (?:the )?(?:secret|api key))",
            Pattern.CASE_INSENSITIVE);

    /** T01 abuse-case and T13 impersonation phrasing that must also flag a draft drop. */
    static final Pattern THREAT_MODEL_INJECTION = Pattern.compile(
            "(?:ignore (?:the )?confirmation|send (?:my )?contents|to every contact|say confirmed"
                    + "|you are now|new (?:system )?instructions|jailbreak"
                    + "|official (?:whatsapp|telegram|imessage) (?:support|alert)"
                    + "|this is (?:whatsapp|telegram) official)",
            Pattern.CASE_INSENSITIVE);

    private InjectionDetector() {}

    public static boolean containsUntrustedInstruction(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return TS_MALICIOUS_INSTRUCTION.matcher(body).find()
                || THREAT_MODEL_INJECTION.matcher(body).find();
    }
}
