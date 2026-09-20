package com.textureflow.connection;

import java.util.Collections;
import java.util.List;

/** No-op remote gateway used while Convex is stubbed out. */
public final class StubRemoteGateway implements RemoteGateway {
    @Override
    public void registerDevice() {}

    @Override
    public void heartbeat() {}

    @Override
    public void uploadEvent(String eventJson, String traceId) {}

    @Override
    public void uploadRemovedEvent(String eventJson, String traceId) {}

    @Override
    public void uploadReceipt(String receiptJson, ClaimRecord claim) {}

    @Override
    public List<RemoteCommand> pollCommands(int limit) {
        return Collections.emptyList();
    }

    @Override
    public ClaimResult claimCommand(String commandId, String claimToken) {
        return new ClaimResult(false, "stubbed", null);
    }

    @Override
    public RemoteProposal loadProposal(String proposalId) {
        return null;
    }

    @Override
    public StartResult startExecution(String commandId, String claimToken) {
        return new StartResult(false, "stubbed", null);
    }
}
