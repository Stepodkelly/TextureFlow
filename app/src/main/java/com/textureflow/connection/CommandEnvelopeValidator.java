package com.textureflow.connection;

public final class CommandEnvelopeValidator {
    private CommandEnvelopeValidator() {}

    public static void requireExecutableTarget(
            RemoteCommand command, String expectedOwnerId, String expectedDeviceId) {
        require(command != null, "Command is required");
        require(command.contractVersion() == 1, "Unsupported command contract version");
        require("LIVE".equals(command.sourceMode()), "Rehearsal commands cannot execute on Android");
        require("QUEUED".equals(command.status())
                        || "CLAIMED".equals(command.status())
                        || "EXECUTING".equals(command.status()),
                "Command is not executable");
        require(same(expectedOwnerId, command.ownerId()), "Command owner does not match runtime owner");
        require(same(expectedDeviceId, command.targetDeviceId()), "Command targets another device");
    }

    public static void requireExactMatch(
            RemoteCommand command,
            RemoteProposal proposal,
            String expectedOwnerId,
            String expectedDeviceId) {
        require(command != null && proposal != null, "Command and proposal are required");
        requireExecutableTarget(command, expectedOwnerId, expectedDeviceId);
        require("CONFIRMED".equals(proposal.status()) || "COMMITTED".equals(proposal.status()),
                "Proposal is not confirmed");
        require(same(command.ownerId(), proposal.ownerId()), "Proposal owner mismatch");
        require(same(command.proposalId(), proposal.proposalId()), "Proposal identity mismatch");
        require(same(command.targetDeviceId(), proposal.targetDeviceId()), "Proposal device mismatch");
        require(same(command.eventId(), proposal.eventId()), "Proposal event mismatch");
        require(command.expectedEventVersion() == proposal.expectedEventVersion(),
                "Proposal event version mismatch");
        require(command.actionType() == proposal.actionType(), "Proposal action mismatch");
        require(command.payload().equals(proposal.payload()), "Proposal payload mismatch");
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new SecurityException(message);
    }
}
