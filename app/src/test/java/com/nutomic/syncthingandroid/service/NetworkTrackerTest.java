package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class NetworkTrackerTest {

    private ConnectivityManager cm;
    private NetworkTracker tracker;
    private int notified;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        cm = context.getSystemService(ConnectivityManager.class);
        shadowOf(cm).clearAllNetworks();
        tracker = new NetworkTracker(context, () -> notified++);
    }

    @After
    public void tearDown() {
        tracker.stop();
    }

    private static NetworkCapabilities caps(int transport, int... capabilities) {
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addTransportType(transport);
        for (int c : capabilities) {
            shadowOf(caps).addCapability(c);
        }
        return caps;
    }

    /** Makes Android know the network, as it does before any callback. */
    @SuppressWarnings("deprecation")
    private void connected(Network network, int legacyType, NetworkCapabilities caps) {
        shadowOf(cm).addNetwork(network, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                legacyType, 0, true, NetworkInfo.State.CONNECTED));
        shadowOf(cm).setNetworkCapabilities(network, caps);
    }

    private ConnectivityManager.NetworkCallback callback() {
        Set<ConnectivityManager.NetworkCallback> callbacks = shadowOf(cm).getNetworkCallbacks();
        assertThat(callbacks).hasSize(1);
        return callbacks.iterator().next();
    }

    // SPEC:run-conditions:R05
    @Test
    public void kindOf_mapsTransports() {
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_WIFI))).isEqualTo(Transport.WIFI);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_ETHERNET))).isEqualTo(Transport.ETHERNET);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_CELLULAR))).isEqualTo(Transport.MOBILE);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_BLUETOOTH))).isEqualTo(Transport.MOBILE);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_LOWPAN))).isEqualTo(Transport.OTHER);
    }

    // SPEC:run-conditions:R03
    @Test
    public void start_registersOneCallback_stopUnregisters() {
        tracker.start();
        assertThat(shadowOf(cm).getNetworkCallbacks()).hasSize(1);
        tracker.stop();
        assertThat(shadowOf(cm).getNetworkCallbacks()).isEmpty();
        tracker.stop();
    }

    // SPEC:run-conditions:R05
    @Test
    public void snapshot_readsConnectedNetworks_skipsVpn() {
        connected(ShadowNetwork.newInstance(7), ConnectivityManager.TYPE_WIFI,
                caps(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        connected(ShadowNetwork.newInstance(8), ConnectivityManager.TYPE_VPN,
                caps(NetworkCapabilities.TRANSPORT_VPN));
        tracker.start();
        List<NetworkState> snapshot = tracker.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).kind).isEqualTo(Transport.WIFI);
        assertThat(notified).isEqualTo(0);
    }

    // SPEC:run-conditions:R03
    @Test
    public void capabilitiesChanged_notifies_snapshotReadsNetwork() {
        tracker.start();
        Network wifi = ShadowNetwork.newInstance(1);
        NetworkCapabilities wifiCaps = caps(NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        connected(wifi, ConnectivityManager.TYPE_WIFI, wifiCaps);
        callback().onCapabilitiesChanged(wifi, wifiCaps);
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(notified).isEqualTo(1);
        List<NetworkState> snapshot = tracker.snapshot();
        assertThat(snapshot).hasSize(1);
        NetworkState state = snapshot.get(0);
        assertThat(state.kind).isEqualTo(Transport.WIFI);
        assertThat(state.validated).isTrue();
        assertThat(state.metered).isFalse();
        assertThat(state.roaming).isFalse();
    }

    @Test
    public void lost_notifies_snapshotDropsNetwork() {
        tracker.start();
        Network mobile = ShadowNetwork.newInstance(2);
        connected(mobile, ConnectivityManager.TYPE_MOBILE, caps(NetworkCapabilities.TRANSPORT_CELLULAR));
        callback().onCapabilitiesChanged(mobile, caps(NetworkCapabilities.TRANSPORT_CELLULAR));
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(tracker.snapshot()).hasSize(1);
        assertThat(tracker.snapshot().get(0).metered).isTrue();
        assertThat(tracker.snapshot().get(0).roaming).isTrue();

        shadowOf(cm).removeNetwork(mobile);
        callback().onLost(mobile);
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(tracker.snapshot()).isEmpty();
        assertThat(notified).isEqualTo(2);
    }

    // SPEC:run-conditions:R03
    @Test
    public void lostBeforeAndroidDropsIt_snapshotSkipsIt() {
        // Seen on a OnePlus LE2123 (Android 14): onLost arrives while getAllNetworks() still
        // lists the network being torn down, and no later callback follows.
        tracker.start();
        Network wifi = ShadowNetwork.newInstance(5);
        NetworkCapabilities wifiCaps = caps(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        connected(wifi, ConnectivityManager.TYPE_WIFI, wifiCaps);
        callback().onCapabilitiesChanged(wifi, wifiCaps);
        callback().onLost(wifi);
        assertThat(tracker.snapshot()).isEmpty();
    }

    // SPEC:run-conditions:R04
    @Test
    public void snapshot_dropsNetworkAfterMissedLost() {
        tracker.start();
        Network wifi = ShadowNetwork.newInstance(3);
        NetworkCapabilities wifiCaps = caps(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        connected(wifi, ConnectivityManager.TYPE_WIFI, wifiCaps);
        callback().onCapabilitiesChanged(wifi, wifiCaps);
        // Android drops the Wi-Fi, but onLost never arrives.
        shadowOf(cm).removeNetwork(wifi);
        assertThat(tracker.snapshot()).isEmpty();
    }

    // SPEC:run-conditions:R08
    @Test
    public void snapshot_takesMeteredFromAndroidNow() {
        tracker.start();
        Network wifi = ShadowNetwork.newInstance(4);
        NetworkCapabilities unmetered = caps(NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        connected(wifi, ConnectivityManager.TYPE_WIFI, unmetered);
        callback().onCapabilitiesChanged(wifi, unmetered);
        // The user marks the Wi-Fi metered; the callback for it is missed.
        shadowOf(cm).setNetworkCapabilities(wifi,
                caps(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        assertThat(tracker.snapshot().get(0).metered).isTrue();
    }
}
