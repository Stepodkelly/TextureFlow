package com.textureflow.intelligence.model;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.Objects;

/** Wi‑Fi only. Fails closed when the active network cannot be read. */
public final class AndroidNetworkPolicy implements NetworkPolicy {
    private final Context context;

    public AndroidNetworkPolicy(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public boolean allowDownload() {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
