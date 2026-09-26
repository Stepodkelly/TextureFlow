package com.textureflow.intelligence.model;

public final class ModelUnavailableException extends Exception {
    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
