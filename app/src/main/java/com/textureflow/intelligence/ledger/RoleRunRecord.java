package com.textureflow.intelligence.ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Ledger row for one council role invocation. Metrics only — no prompts or bodies. */
public final class RoleRunRecord {
    private final String tickId;
    private final CouncilRole role;
    private final String promptVersion;
    private final int inputTokens;
    private final int outputTokens;
    private final long wallMs;
    private final boolean schemaValid;
    private final List<String> skepticIssues;

    public RoleRunRecord(
            String tickId,
            CouncilRole role,
            String promptVersion,
            int inputTokens,
            int outputTokens,
            long wallMs,
            boolean schemaValid,
            List<String> skepticIssues) {
        this.tickId = Objects.requireNonNull(tickId, "tickId");
        this.role = Objects.requireNonNull(role, "role");
        this.promptVersion = promptVersion == null ? "" : promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.wallMs = wallMs;
        this.schemaValid = schemaValid;
        this.skepticIssues = copyList(skepticIssues);
    }

    public String getTickId() { return tickId; }
    public CouncilRole getRole() { return role; }
    public String getPromptVersion() { return promptVersion; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getWallMs() { return wallMs; }
    public boolean isSchemaValid() { return schemaValid; }
    public List<String> getSkepticIssues() { return skepticIssues; }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
