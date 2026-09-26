package com.textureflow.intelligence.roles;

import com.textureflow.intelligence.model.ModelPort;
import com.textureflow.intelligence.model.ModelRequest;
import com.textureflow.intelligence.model.ModelResponse;
import com.textureflow.intelligence.model.NoModelPort;

/** In-memory port for role unit tests. Mirrors the model-package fake. */
public final class FakeModelPort implements ModelPort {
    public String text = "";
    public Exception generateFailure;
    public int loads;
    public int generates;
    public int unloads;
    public boolean loaded;
    public ModelRequest lastRequest;

    public FakeModelPort() {}

    public FakeModelPort(String text) {
        this.text = text;
    }

    @Override
    public void load() {
        loads++;
        loaded = true;
    }

    @Override
    public ModelResponse generate(ModelRequest request) throws Exception {
        generates++;
        lastRequest = request;
        if (generateFailure != null) {
            throw generateFailure;
        }
        return new ModelResponse(text, 8, 4, 11);
    }

    @Override
    public void unload() {
        unloads++;
        loaded = false;
    }

    @Override
    public int tokenCount(String text) {
        return new NoModelPort().tokenCount(text);
    }

    @Override
    public String runtimeName() {
        return "fake";
    }
}
