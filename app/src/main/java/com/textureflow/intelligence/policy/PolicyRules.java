package com.textureflow.intelligence.policy;

/** Stable names for every doc §6.4 row. Reasons on {@link PolicyVerdict} use these strings. */
public final class PolicyRules {
    public static final String VALID_SCHEMA = "ValidSchema";
    public static final String ALLOWED_ACTION_TYPE = "AllowedActionType";
    public static final String RECIPIENT_BINDING = "RecipientBinding";
    public static final String EVENT_FRESHNESS = "EventFreshness";
    public static final String INJECTION_NO_DRAFT = "InjectionNoDraft";
    public static final String SKEPTIC_OK = "SkepticOk";
    public static final String NO_INVENTED_SECRETS = "NoInventedSecrets";
    public static final String URGENT_CONFIDENCE_CAP = "UrgentConfidenceCap";

    private PolicyRules() {}
}
