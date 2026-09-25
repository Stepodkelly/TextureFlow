package com.textureflow.intelligence.policy;

/** Named JSON contracts. Role schemas follow doc §6.1; *_V1 ports {@code schemas.ts}. */
public enum SchemaName {
    TRIAGE("triage"),
    SUMMARIZER("summarizer"),
    DRAFTER("drafter"),
    SKEPTIC("skeptic"),
    SUMMARY_V1("summary_v1"),
    DRAFT_V1("draft_v1");

    private final String wireName;

    SchemaName(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static SchemaName fromWire(String value) {
        if (value == null) {
            throw new SchemaValidationException("Schema name is required.");
        }
        String trimmed = value.trim();
        for (SchemaName schema : values()) {
            if (schema.wireName.equals(trimmed) || schema.name().equalsIgnoreCase(trimmed)) {
                return schema;
            }
        }
        throw new SchemaValidationException("Unknown schema: " + value);
    }
}
