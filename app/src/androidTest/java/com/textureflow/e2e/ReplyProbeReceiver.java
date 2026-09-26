package com.textureflow.e2e;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Test-APK sink for the fake MessagingStyle RemoteInput action. Never sends SMS. */
public final class ReplyProbeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Dry-run only: the e2e suite never fills this RemoteInput.
    }
}
