package com.textureflow.intelligence.triage;

/** Verbatim {@code priorityReason} strings from {@code intelligence/src/priority.ts}. */
public final class PriorityReasons {
    public static final String MALICIOUS =
            "The notification contains untrusted instruction-like content and needs review.";
    public static final String PROMOTIONAL =
            "Promotional language was detected without a direct or urgent request.";
    public static final String URGENT_DIRECT_REQUEST =
            "A recent, direct request contains immediate timing or access signals.";
    public static final String URGENCY =
            "The message contains a recent, immediate timing or safety signal.";
    public static final String DIRECT_REQUEST =
            "A recent direct question or request likely needs a response.";
    public static final String IMPORTANT_CONTACT =
            "A recent message came from an important contact.";
    public static final String DEFAULT =
            "A recent notification has no strong urgency or request signal.";

    private PriorityReasons() {}
}
