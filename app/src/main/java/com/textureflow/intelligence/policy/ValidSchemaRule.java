package com.textureflow.intelligence.policy;

/** §6.4: JSON parses and matches schema; lengths within caps → drop model output. */
public final class ValidSchemaRule implements PolicyRule {
    private final SchemaValidator validator;

    public ValidSchemaRule(SchemaValidator validator) {
        this.validator = validator;
    }

    @Override
    public String name() {
        return PolicyRules.VALID_SCHEMA;
    }

    @Override
    public void evaluate(PolicyRequest request, PolicyAccumulator accumulator) {
        if (request.modelJson() == null) {
            return;
        }
        if (request.schema() == null) {
            accumulator.drop(name());
            return;
        }
        try {
            accumulator.parsedJson(validator.validate(request.schema(), request.modelJson()));
        } catch (SchemaValidationException e) {
            accumulator.drop(name());
        }
    }
}
