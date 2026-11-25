package com.nutomic.syncthingandroid.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports the connected physical networks (spec R03, R05, R08). The only class that asks
 * Android about networks. A {@link ConnectivityManager.NetworkCallback} says when to look
 * again; {@link #snapshot()} always reads the networks from Android itself, and VPNs are left out.
 */
final class NetworkTracker {

    private static final String TAG = "NetworkTracker";

    interface Listener {
        /** Called on the main thread after any network appeared, changed or was lost. */
        void onNetworksChanged();
    }

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final Listener mListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * Capabilities as the callback delivered them, kept for the Wi-Fi name (API 31+ includes
     * it only there). Guarded by itself; written on the connectivity thread.
     */
    private final Map<Network, NetworkCapabilities> mNetworks = new HashMap<>();
    /**
     * Networks the callback reported lost while Android may still list them as they are torn
     * down: onLost can arrive before getAllNetworks() drops the network, and no callback follows.
     * Guarded by {@link #mNetworks}. Network ids are not reused, so an entry is only dropped once
     * Android no longer lists the network.
     */
    private final Set<Network> mLost = new HashSet<>();
    @Nullable private ConnectivityManager.NetworkCallback mCallback;

    NetworkTracker(Context context, Listener listener) {
        mContext = context.getApplicationContext();
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mListener = listener;
    }

    void start() {
        if (mCallback != null) {
            return;
        }
        mCallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
                : new Callback();
        try {
            mConnectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), mCallback);
        } catch (RuntimeException e) {
            // Android limits the callbacks per app. Without one, the screen-on and 15-minute
            // re-checks still read the networks afresh through snapshot().
            Log.e(TAG, "registerNetworkCallback failed", e);
            mCallback = null;
        }
    }

    void stop() {
        if (mCallback != null) {
            try {
                mConnectivityManager.unregisterNetworkCallback(mCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "unregisterNetworkCallback: callback was not registered", e);
            }
            mCallback = null;
        }
        synchronized (mNetworks) {
            mNetworks.clear();
            mLost.clear();
        }
        mMainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * The connected physical networks, the default one marked, read from Android now: a missed
     * callback (#405) or one never registered cannot leave a network behind (spec R04). The
     * callback's own capabilities are used only for the Wi-Fi name, which from API 31 is not in
     * {@link ConnectivityManager#getNetworkCapabilities}.
     */
    @SuppressWarnings("deprecation")
    List<NetworkState> snapshot() {
        Network[] all;
        try {
            all = mConnectivityManager.getAllNetworks();
        } catch (RuntimeException e) {
            Log.w(TAG, "getAllNetworks failed", e);
            all = new Network[0];
        }
        Map<Network, NetworkCapabilities> fromCallback;
        Set<Network> lost;
        synchronized (mNetworks) {
            // Forget networks Android no longer has, so neither cache can grow or go stale.
            Map<Network, NetworkCapabilities> kept = new HashMap<>();
            Set<Network> stillLost = new HashSet<>();
            for (Network network : all) {
                NetworkCapabilities caps = mNetworks.get(network);
                if (caps != null) {
                    kept.put(network, caps);
                }
                if (mLost.contains(network)) {
                    stillLost.add(network);
                }
            }
            mNetworks.clear();
            mNetworks.putAll(kept);
            mLost.clear();
            mLost.addAll(stillLost);
            fromCallback = kept;
            lost = stillLost;
        }
        Network active = mConnectivityManager.getActiveNetwork();
        List<NetworkState> states = new ArrayList<>(all.length);
        for (Network network : all) {
            NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(network);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || lost.contains(network)) {
                continue;
            }
            NetworkCapabilities nameSource = fromCallback.containsKey(network) ? fromCallback.get(network) : caps;
            states.add(toState(network, caps, nameSource, network.equals(active)));
        }
        return states;
    }

    /** R05's table. VPNs never reach here; anything unknown is OTHER. */
    @VisibleForTesting
    static Transport kindOf(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return Transport.WIFI;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return Transport.ETHERNET;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return Transport.MOBILE;
        }
        return Transport.OTHER;
    }

    private NetworkState toState(Network network, NetworkCapabilities caps, NetworkCapabilities nameSource,
                                 boolean isDefault) {
        Transport kind = kindOf(caps);
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        boolean wifiInfoAvailable = false;
        String ssid = null;
        if (kind == Transport.WIFI) {
            WifiInfo info = wifiInfo(nameSource);
            if (info != null) {
                wifiInfoAvailable = true;
                ssid = info.getSSID();
            }
        }
        return new NetworkState(kind, isDefault, validated, metered, isRoaming(network, caps),
                wifiInfoAvailable, ssid);
    }

    @SuppressWarnings("deprecation")
    private boolean isRoaming(Network network, NetworkCapabilities caps) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        }
        NetworkInfo info = mConnectivityManager.getNetworkInfo(network);
        return info != null && info.isRoaming();
    }

    /**
     * From API 31 the Wi-Fi name is in the callback's capabilities, and only there; before it,
     * the Wi-Fi manager reports the connected Wi-Fi. Both need location access (R08).
     */
    @Nullable
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private WifiInfo wifiInfo(NetworkCapabilities caps) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return wifiInfoFromCaps(caps);
        }
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null ? wifiManager.getConnectionInfo() : null;
    }

    @Nullable
    @RequiresApi(Build.VERSION_CODES.S)
    private static WifiInfo wifiInfoFromCaps(NetworkCapabilities caps) {
        TransportInfo info = caps.getTransportInfo();
        return info instanceof WifiInfo ? (WifiInfo) info : null;
    }

    private void changed() {
        mMainHandler.post(mListener::onNetworksChanged);
    }

    private final class Callback extends ConnectivityManager.NetworkCallback {

        Callback() {
            super();
        }

        @RequiresApi(Build.VERSION_CODES.S)
        Callback(int flags) {
            super(flags);
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            // Before API 26, onCapabilitiesChanged is not guaranteed to follow.
            changed();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            synchronized (mNetworks) {
                mNetworks.put(network, caps);
                mLost.remove(network);
            }
            changed();
        }

        @Override
        public void onLost(@NonNull Network network) {
            synchronized (mNetworks) {
                mNetworks.remove(network);
                mLost.add(network);
            }
            changed();
        }
    }
}
