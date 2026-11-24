package com.nutomic.syncthingandroid.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Snapshot of the device state that run conditions are evaluated against.
 * Filled in by {@link RunConditionMonitor} (network fields through {@link NetworkSnapshot});
 * evaluated by {@link RunConditionEvaluator}.
 */
public final class DeviceConditions {

    public enum Transport {
        /** No physical network connected. Treated as flight mode. */
        NO_ACTIVE_NETWORK,
        WIFI,
        ETHERNET,
        /** Mobile data, including Bluetooth tethering. */
        MOBILE,
        OTHER
    }

    public final boolean charging;
    public final boolean powerSaving;
    public final boolean masterSyncEnabled;
    /** Kind of the physical network that carries internet traffic, a VPN looked through. */
    @NonNull public final Transport transport;
    public final boolean wifiConnected;
    /** The connected Wi-Fi reaches the internet. */
    public final boolean wifiValidated;
    /** The connected Wi-Fi's own metered state. */
    public final boolean wifiMetered;
    public final boolean ethernetConnected;
    public final boolean mobileConnected;
    /** The connected mobile network is roaming. */
    public final boolean roaming;
    public final boolean wifiInfoAvailable;
    @Nullable public final String wifiSsid;

    private DeviceConditions(Builder b) {
        charging = b.charging;
        powerSaving = b.powerSaving;
        masterSyncEnabled = b.masterSyncEnabled;
        transport = b.transport;
        wifiConnected = b.wifiConnected;
        wifiValidated = b.wifiValidated;
        wifiMetered = b.wifiMetered;
        ethernetConnected = b.ethernetConnected;
        mobileConnected = b.mobileConnected;
        roaming = b.roaming;
        wifiInfoAvailable = b.wifiInfoAvailable;
        wifiSsid = b.wifiSsid;
    }

    /** At least one physical network is connected. */
    public boolean isConnected() {
        return transport != Transport.NO_ACTIVE_NETWORK;
    }

    @NonNull
    @Override
    public String toString() {
        // Never print the Wi-Fi name.
        return "DeviceConditions{charging=" + charging +
                ", powerSaving=" + powerSaving +
                ", masterSyncEnabled=" + masterSyncEnabled +
                ", transport=" + transport +
                ", wifi=" + wifiConnected +
                ", wifiValidated=" + wifiValidated +
                ", wifiMetered=" + wifiMetered +
                ", ethernet=" + ethernetConnected +
                ", mobile=" + mobileConnected +
                ", roaming=" + roaming +
                ", wifiInfoAvailable=" + wifiInfoAvailable + "}";
    }

    public static final class Builder {
        private boolean charging = false;
        private boolean powerSaving = false;
        private boolean masterSyncEnabled = true;
        private Transport transport = Transport.NO_ACTIVE_NETWORK;
        private boolean wifiConnected = false;
        private boolean wifiValidated = false;
        private boolean wifiMetered = false;
        private boolean ethernetConnected = false;
        private boolean mobileConnected = false;
        private boolean roaming = false;
        private boolean wifiInfoAvailable = false;
        private String wifiSsid = null;

        public Builder charging(boolean v) { charging = v; return this; }
        public Builder powerSaving(boolean v) { powerSaving = v; return this; }
        public Builder masterSyncEnabled(boolean v) { masterSyncEnabled = v; return this; }
        public Builder transport(@NonNull Transport v) { transport = v; return this; }
        public Builder wifiConnected(boolean v) { wifiConnected = v; return this; }
        public Builder wifiValidated(boolean v) { wifiValidated = v; return this; }
        public Builder wifiMetered(boolean v) { wifiMetered = v; return this; }
        public Builder ethernetConnected(boolean v) { ethernetConnected = v; return this; }
        public Builder mobileConnected(boolean v) { mobileConnected = v; return this; }
        public Builder roaming(boolean v) { roaming = v; return this; }
        public Builder wifiInfoAvailable(boolean v) { wifiInfoAvailable = v; return this; }
        public Builder wifiSsid(@Nullable String v) { wifiSsid = v; return this; }

        public DeviceConditions build() {
            return new DeviceConditions(this);
        }
    }
}
