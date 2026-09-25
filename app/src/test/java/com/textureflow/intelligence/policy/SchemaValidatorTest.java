package com.textureflow.intelligence.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class SchemaValidatorTest {
    private final SchemaValidator validator = new SchemaValidator();

    @Test
    public void acceptsDocRoleOutputs() {
        validator.validate(SchemaName.TRIAGE, ""
                + "{\"intent\":\"QUESTION\",\"requiresResponse\":true,\"urgency\":\"NORMAL\","
                + "\"reason\":\"A recent question.\",\"modelConfidence\":0.61}");
        validator.validate(SchemaName.SUMMARIZER, ""
                + "{\"summary\":\"Sam is downstairs.\",\"mentions\":[\"Sam\"],\"ambiguities\":[]}");
        validator.validate(SchemaName.DRAFTER, "{\"replyText\":\"I'm coming down.\",\"tone\":\"DIRECT\"}");
        validator.validate(SchemaName.SKEPTIC, "{\"ok\":true,\"issues\":[]}");
    }

    @Test
    public void acceptsTypeScriptNamedSchemas() {
        validator.validate(SchemaName.SUMMARY_V1, ""
                + "{\"summary\":\"Sam is downstairs and cannot enter.\","
                + "\"priorityScore\":0.94,\"priorityLevel\":\"URGENT\","
                + "\"priorityReason\":\"A close contact is waiting outside.\","
                + "\"intent\":\"REQUEST_FOR_IMMEDIATE_ACTION\",\"requiresResponse\":true,"
                + "\"ambiguities\":[]}");
        validator.validate(SchemaName.DRAFT_V1, ""
                + "{\"text\":\"I'm coming downstairs now.\",\"tone\":\"DIRECT\","
                + "\"confidence\":0.91,\"ambiguities\":[]}");
    }

    @Test
    public void rejectsUnknownFieldsMissingKeysAndOverLength() {
        String valid = "{\"replyText\":\"I'm coming down.\",\"tone\":\"DIRECT\"}";
        SchemaValidationException unknown = assertThrows(
                SchemaValidationException.class,
                () -> validator.validate(SchemaName.DRAFTER, valid.replace("}", ",\"command\":\"SEND_NOW\"}")));
        assertTrue(unknown.getMessage().contains("unknown keys"));

        SchemaValidationException missing = assertThrows(
                SchemaValidationException.class,
                () -> validator.validate(SchemaName.DRAFTER, "{\"replyText\":\"I'm coming down.\"}"));
        assertTrue(missing.getMessage().contains("missing keys"));

        String tooLong = "{\"replyText\":\"" + "x".repeat(281) + "\",\"tone\":\"DIRECT\"}";
        assertThrows(SchemaValidationException.class, () -> validator.validate(SchemaName.DRAFTER, tooLong));
    }

    @Test
    public void rejectsUnknownSkepticIssuesAndNonObjects() {
        assertThrows(
                SchemaValidationException.class,
                () -> validator.validate(SchemaName.SKEPTIC, "{\"ok\":false,\"issues\":[\"exfiltrate\"]}"));
        assertThrows(SchemaValidationException.class, () -> validator.validate(SchemaName.TRIAGE, "[1]"));
        JSONObject parsed = validator.validate(
                SchemaName.SKEPTIC,
                "{\"ok\":false,\"issues\":[\"follows_injection\",\"wrong_recipient\"]}");
        assertEquals(false, parsed.optBoolean("ok"));
    }

    @Test
    public void rejectsOutOfRangeConfidence() {
        assertThrows(
                SchemaValidationException.class,
                () -> validator.validate(
                        SchemaName.TRIAGE,
                        "{\"intent\":\"QUESTION\",\"requiresResponse\":true,\"urgency\":\"NORMAL\","
                                + "\"reason\":\"A recent question.\",\"modelConfidence\":1.5}"));
    }
}
