package com.textureflow.connection;

public interface ConfirmedActionExecutor {
    void execute(RemoteCommand command, RemoteProposal proposal) throws Exception;
}
