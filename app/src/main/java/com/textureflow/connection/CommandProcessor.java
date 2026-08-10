package com.textureflow.connection;

import java.util.List;

public final class CommandProcessor {
    public record ProcessResult(int observed, int claimed, int executed) {}

    private final RemoteGateway gateway;
    private final ClaimStore claims;
    private final ConfirmedActionExecutor executor;
    private final String ownerId;
    private final String deviceId;

    public CommandProcessor(
            RemoteGateway gateway,
            ClaimStore claims,
            ConfirmedActionExecutor executor,
            String ownerId,
            String deviceId) {
        this.gateway = gateway;
        this.claims = claims;
        this.executor = executor;
        this.ownerId = ownerId;
        this.deviceId = deviceId;
    }

    public ProcessResult pollAndProcess(long nowMillis, int limit) throws Exception {
        List<RemoteCommand> commands = gateway.pollCommands(limit);
        int claimedCount = 0;
        int executedCount = 0;
        for (RemoteCommand observed : commands) {
            if (!"LIVE".equals(observed.sourceMode())) continue;
            CommandEnvelopeValidator.requireExecutableTarget(observed, ownerId, deviceId);
            ClaimRecord persisted = claims.getOrCreate(observed, nowMillis);
            RemoteGateway.ClaimResult claim = gateway.claimCommand(
                    observed.commandId(), persisted.claimToken());
            if (!claim.claimed()) continue;
            claimedCount++;
            RemoteCommand claimedCommand = claim.command() == null ? observed : claim.command();
            RemoteProposal proposal = gateway.loadProposal(claimedCommand.proposalId());
            CommandEnvelopeValidator.requireExactMatch(claimedCommand, proposal, ownerId, deviceId);
            RemoteGateway.StartResult started = gateway.startExecution(
                    claimedCommand.commandId(), persisted.claimToken());
            if (!started.executable()) continue;
            executor.execute(claimedCommand, proposal);
            executedCount++;
        }
        return new ProcessResult(commands.size(), claimedCount, executedCount);
    }
}
