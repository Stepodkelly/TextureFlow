package com.textureflow.intelligence.model;

/** Progress for Stream L's download UI. {@code totalBytes} is 0 when unknown. */
public interface DownloadListener {
    void onProgress(long bytesWritten, long totalBytes);
}
