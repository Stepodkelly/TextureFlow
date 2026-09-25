package com.textureflow.intelligence.triage;

import java.util.Objects;

public final class PersonAlias {
    private final String value;
    private final String packageName;

    public PersonAlias(String value) {
        this(value, null);
    }

    public PersonAlias(String value, String packageName) {
        this.value = Objects.requireNonNull(value, "value");
        this.packageName = packageName == null || packageName.isEmpty() ? null : packageName;
    }

    public String getValue() { return value; }
    public String getPackageName() { return packageName; }
}
