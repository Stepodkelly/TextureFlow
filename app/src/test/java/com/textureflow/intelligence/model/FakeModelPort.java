package com.textureflow.intelligence.model;

/** In-memory port for unit tests. Never touches the 529 MB .task. */
final class FakeModelPort implements ModelPort {
    String text = "";
    Exception generateFailure;
    int loads;
    int generates;
    int unloads;
    boolean loaded;

    @Override
    public void load() {
        loads++;
        loaded = true;
    }

    @Override
    public ModelResponse generate(ModelRequest request) throws Exception {
        generates++;
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
