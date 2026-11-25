package com.nutomic.syncthingandroid.service;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

import java.util.List;

/**
 * Turns the connected physical networks into the network fields of {@link DeviceConditions}
 * (spec R05, R07, R08). Pure, so it is tested without Android's connectivity service.
 */
public final class NetworkSnapshot {

    private NetworkSnapshot() {
    }

    public static void fill(DeviceConditions.Builder b, List<NetworkState> physical) {
        NetworkState wifi = null;
        NetworkState mobile = null;
        boolean ethernet = false;
        for (NetworkState n : physical) {
            switch (n.kind) {
                case WIFI:
                    if (wifi == null || (!wifi.validated && n.validated)) {
                        wifi = n;
                    }
                    break;
                case MOBILE:
                    if (mobile == null || n.isDefault) {
                        mobile = n;
                    }
                    break;
                case ETHERNET:
                    ethernet = true;
                    break;
                default:
                    break;
            }
        }

        b.transport(defaultTransport(physical));
        b.ethernetConnected(ethernet);
        b.wifiConnected(wifi != null)
                .wifiValidated(wifi != null && wifi.validated)
                .wifiMetered(wifi != null && wifi.metered)
                .wifiInfoAvailable(wifi != null && wifi.wifiInfoAvailable)
                .wifiSsid(wifi != null ? wifi.wifiSsid : null);
        b.mobileConnected(mobile != null)
                .roaming(mobile != null && mobile.roaming);
    }

    /**
     * The kind of the network that carries internet traffic. When a VPN is the default network,
     * no physical network is marked default; the one Android would route the VPN over is then
     * taken by the order Ethernet, validated Wi-Fi, mobile, Wi-Fi without internet, other.
     */
    private static Transport defaultTransport(List<NetworkState> physical) {
        if (physical.isEmpty()) {
            return Transport.NO_ACTIVE_NETWORK;
        }
        for (NetworkState n : physical) {
            if (n.isDefault) {
                return n.kind;
            }
        }
        NetworkState best = null;
        for (NetworkState n : physical) {
            if (best == null || rank(n) < rank(best)) {
                best = n;
            }
        }
        return best.kind;
    }

    private static int rank(NetworkState n) {
        switch (n.kind) {
            case ETHERNET:
                return 0;
            case WIFI:
                return n.validated ? 1 : 3;
            case MOBILE:
                return 2;
            default:
                return 4;
        }
    }
}
