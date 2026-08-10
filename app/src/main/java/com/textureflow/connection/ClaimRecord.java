package com.textureflow.connection;

public record ClaimRecord(
        String commandId,
        String claimToken,
        String traceId,
        String proposalId,
        long createdAtMillis) {

    public ClaimRecord {
        require(commandId, "Command ID");
        require(claimToken, "Claim token");
        require(traceId, "Trace ID");
        require(proposalId, "Proposal ID");
        if (claimToken.length() < 16 || claimToken.length() > 256) {
            throw new IllegalArgumentException("Claim token must contain 16 to 256 characters");
        }
    }

    private static void require(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
