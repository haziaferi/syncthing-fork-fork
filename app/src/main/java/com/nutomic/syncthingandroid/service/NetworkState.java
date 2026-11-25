package com.nutomic.syncthingandroid.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

/**
 * One connected physical network, as {@link NetworkTracker} saw it (spec R05).
 * A VPN never becomes a NetworkState: the run conditions judge the networks beneath it.
 */
public final class NetworkState {

    /** {@link Transport#WIFI}, {@link Transport#ETHERNET}, {@link Transport#MOBILE} or {@link Transport#OTHER}. */
    @NonNull public final Transport kind;
    /** True for the network Android sends internet traffic through. */
    public final boolean isDefault;
    /** Android has confirmed the network reaches the internet. */
    public final boolean validated;
    public final boolean metered;
    public final boolean roaming;
    /** Only for Wi-Fi: whether Android returned connection info at all. */
    public final boolean wifiInfoAvailable;
    /** Only for Wi-Fi: the quoted name, null or {@code <unknown ssid>} when Android hides it. */
    @Nullable public final String wifiSsid;

    public NetworkState(@NonNull Transport kind, boolean isDefault, boolean validated, boolean metered,
                        boolean roaming, boolean wifiInfoAvailable, @Nullable String wifiSsid) {
        this.kind = kind;
        this.isDefault = isDefault;
        this.validated = validated;
        this.metered = metered;
        this.roaming = roaming;
        this.wifiInfoAvailable = wifiInfoAvailable;
        this.wifiSsid = wifiSsid;
    }

    @NonNull
    @Override
    public String toString() {
        // Never print the Wi-Fi name.
        return "NetworkState{" + kind + (isDefault ? ", default" : "") + ", validated=" + validated
                + ", metered=" + metered + ", roaming=" + roaming + "}";
    }
}
