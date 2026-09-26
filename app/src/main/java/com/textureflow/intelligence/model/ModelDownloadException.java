package com.textureflow.intelligence.model;

public final class ModelDownloadException extends Exception {
    public ModelDownloadException(String message) {
        super(message);
    }

    public ModelDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
