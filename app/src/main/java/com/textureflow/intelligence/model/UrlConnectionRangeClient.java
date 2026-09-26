package com.textureflow.intelligence.model;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Resumable HTTPS GET via {@link HttpURLConnection}. */
public final class UrlConnectionRangeClient implements HttpRangeClient {
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public UrlConnectionRangeClient() {
        this(30_000, 60_000);
    }

    public UrlConnectionRangeClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public HttpRangeResponse open(String url, long startByte) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod("GET");
        if (startByte > 0) {
            connection.setRequestProperty("Range", "bytes=" + startByte + "-");
        }
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect();
            throw new IOException("Download failed with HTTP " + code);
        }
        boolean partial = code == HttpURLConnection.HTTP_PARTIAL;
        return new HttpRangeResponse(
                connection.getInputStream(),
                partial,
                connection.getContentLengthLong());
    }
}
