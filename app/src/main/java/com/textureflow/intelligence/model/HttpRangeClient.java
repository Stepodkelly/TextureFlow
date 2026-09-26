package com.textureflow.intelligence.model;

import java.io.IOException;

/** Opens an HTTP GET, optionally with a Range resume. */
public interface HttpRangeClient {
    HttpRangeResponse open(String url, long startByte) throws IOException;
}
