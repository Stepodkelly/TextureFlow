package com.textureflow.intelligence.policy;

interface PolicyRule {
    String name();

    void evaluate(PolicyRequest request, PolicyAccumulator accumulator);
}
