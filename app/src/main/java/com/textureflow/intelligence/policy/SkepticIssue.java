package com.textureflow.intelligence.policy;

public enum SkepticIssue {
    FOLLOWS_INJECTION("follows_injection"),
    WRONG_RECIPIENT("wrong_recipient"),
    LEAKS_DATA("leaks_data"),
    CHANGES_MEANING("changes_meaning");

    private final String wireName;

    SkepticIssue(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static SkepticIssue fromWire(String value) {
        if (value == null) {
            throw new SchemaValidationException("Skeptic issue is required.");
        }
        for (SkepticIssue issue : values()) {
            if (issue.wireName.equals(value)) {
                return issue;
            }
        }
        throw new SchemaValidationException("issues has an unsupported value.");
    }
}
