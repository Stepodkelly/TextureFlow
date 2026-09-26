package com.textureflow.intelligence.model;

import java.io.InputStream;
import java.util.Objects;

public final class HttpRangeResponse implements AutoCloseable {
    private final InputStream body;
    private final boolean partial;
    private final long contentLength;

    public HttpRangeResponse(InputStream body, boolean partial, long contentLength) {
        this.body = Objects.requireNonNull(body, "body");
        this.partial = partial;
        this.contentLength = contentLength;
    }

    public InputStream body() {
        return body;
    }

    public boolean partial() {
        return partial;
    }

    public long contentLength() {
        return contentLength;
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
