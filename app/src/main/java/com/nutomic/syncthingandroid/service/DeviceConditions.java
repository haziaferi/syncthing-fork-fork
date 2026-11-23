package com.nutomic.syncthingandroid.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Snapshot of the device state that run conditions are evaluated against.
 * Filled in by {@link RunConditionMonitor}; evaluated by {@link RunConditionEvaluator}.
 */
public final class DeviceConditions {

    public enum Transport {
        /** No active network at all. Treated as flight mode. */
        NO_ACTIVE_NETWORK,
        /** An active network exists but is not connected. */
        DISCONNECTED,
        /** Wi-Fi or WiMAX. */
        WIFI,
        ETHERNET,
        /** Mobile data, including Bluetooth tethering, DUN and HIPRI. */
        MOBILE,
        OTHER
    }

    public final boolean charging;
    public final boolean powerSaving;
    public final boolean masterSyncEnabled;
    @NonNull public final Transport transport;
    public final boolean activeNetworkMetered;
    public final boolean roaming;
    public final boolean wifiInfoAvailable;
    @Nullable public final String wifiSsid;

    private DeviceConditions(Builder b) {
        charging = b.charging;
        powerSaving = b.powerSaving;
        masterSyncEnabled = b.masterSyncEnabled;
        transport = b.transport;
        activeNetworkMetered = b.activeNetworkMetered;
        roaming = b.roaming;
        wifiInfoAvailable = b.wifiInfoAvailable;
        wifiSsid = b.wifiSsid;
    }

    public boolean isConnected() {
        return transport != Transport.NO_ACTIVE_NETWORK && transport != Transport.DISCONNECTED;
    }

    @NonNull
    @Override
    public String toString() {
        return "DeviceConditions{charging=" + charging +
                ", powerSaving=" + powerSaving +
                ", masterSyncEnabled=" + masterSyncEnabled +
                ", transport=" + transport +
                ", metered=" + activeNetworkMetered +
                ", roaming=" + roaming +
                ", wifiInfoAvailable=" + wifiInfoAvailable + "}";
    }

    public static final class Builder {
        private boolean charging = false;
        private boolean powerSaving = false;
        private boolean masterSyncEnabled = true;
        private Transport transport = Transport.NO_ACTIVE_NETWORK;
        private boolean activeNetworkMetered = false;
        private boolean roaming = false;
        private boolean wifiInfoAvailable = false;
        private String wifiSsid = null;

        public Builder charging(boolean v) { charging = v; return this; }
        public Builder powerSaving(boolean v) { powerSaving = v; return this; }
        public Builder masterSyncEnabled(boolean v) { masterSyncEnabled = v; return this; }
        public Builder transport(@NonNull Transport v) { transport = v; return this; }
        public Builder activeNetworkMetered(boolean v) { activeNetworkMetered = v; return this; }
        public Builder roaming(boolean v) { roaming = v; return this; }
        public Builder wifiInfoAvailable(boolean v) { wifiInfoAvailable = v; return this; }
        public Builder wifiSsid(@Nullable String v) { wifiSsid = v; return this; }

        public DeviceConditions build() {
            return new DeviceConditions(this);
        }
    }
}
