package com.textureflow.connection;

import java.util.List;

public interface RemoteGateway {
    record ClaimResult(boolean claimed, String reason, RemoteCommand command) {}
    record StartResult(boolean executable, String operation, RemoteCommand command) {}

    void registerDevice() throws Exception;
    void heartbeat() throws Exception;
    void uploadEvent(String eventJson, String traceId) throws Exception;
    void uploadRemovedEvent(String eventJson, String traceId) throws Exception;
    void uploadReceipt(String receiptJson, ClaimRecord claim) throws Exception;
    List<RemoteCommand> pollCommands(int limit) throws Exception;
    ClaimResult claimCommand(String commandId, String claimToken) throws Exception;
    RemoteProposal loadProposal(String proposalId) throws Exception;
    StartResult startExecution(String commandId, String claimToken) throws Exception;
}
