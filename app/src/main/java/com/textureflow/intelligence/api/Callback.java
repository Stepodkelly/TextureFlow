package com.textureflow.intelligence.api;

/** Async result for summary and draft requests. */
public interface Callback<T> {
    void onResult(T value);

    void onError(Exception error);
}
