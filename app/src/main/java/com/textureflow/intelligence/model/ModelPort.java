package com.textureflow.intelligence.model;

/** On-device generate surface. Implementations must not send data off-device. */
public interface ModelPort {
    void load() throws Exception;

    ModelResponse generate(ModelRequest request) throws Exception;

    void unload();

    int tokenCount(String text);

    String runtimeName();
}
