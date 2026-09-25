package com.textureflow.intelligence.policy;

public final class SchemaValidationException extends RuntimeException {
    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
