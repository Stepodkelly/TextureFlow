package com.textureflow.connection;

import android.content.Context;

import com.textureflow.notifications.TextureNotificationListenerService;

public final class AndroidConfirmedActionExecutor implements ConfirmedActionExecutor {
    private final Context context;

    public AndroidConfirmedActionExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void execute(RemoteCommand command, RemoteProposal proposal) {
        TextureNotificationListenerService.executeConfirmed(
                context, command.toLocalClaimedCommand(), proposal.toConfirmedProposal());
        // The executor transactionally creates RECEIPT_COMPLETE; the outbox owns network delivery.
    }
}
