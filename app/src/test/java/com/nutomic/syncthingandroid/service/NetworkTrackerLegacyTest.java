package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
import org.robolectric.shadows.ShadowWifiInfo;

import java.util.List;

/**
 * The tracker's paths for older Android versions (spec R03, R08): roaming from NetworkInfo
 * below API 28, the Wi-Fi name from WifiManager below API 31, and onAvailable alone below API 26.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = {24, 30})
public class NetworkTrackerLegacyTest {

    private Context context;
    private ConnectivityManager cm;
    private NetworkTracker tracker;
    private int notified;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).clearAllNetworks();
        tracker = new NetworkTracker(context, () -> notified++);
        tracker.start();
    }

    @After
    public void tearDown() {
        tracker.stop();
    }

    @SuppressWarnings("deprecation")
    private NetworkInfo connected(Network network, int legacyType, int transport, int... capabilities) {
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addTransportType(transport);
        for (int c : capabilities) {
            shadowOf(caps).addCapability(c);
        }
        NetworkInfo info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                legacyType, 0, true, NetworkInfo.State.CONNECTED);
        shadowOf(cm).addNetwork(network, info);
        shadowOf(cm).setNetworkCapabilities(network, caps);
        return info;
    }

    // SPEC:run-conditions:R08
    @Test
    @Config(sdk = 24)
    @SuppressWarnings("deprecation")
    public void roamingBelowApi28_readsNetworkInfo() throws Exception {
        NetworkInfo info = connected(ShadowNetwork.newInstance(21), ConnectivityManager.TYPE_MOBILE,
                NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        assertThat(tracker.snapshot().get(0).roaming).isFalse();
        // NetworkInfo.setRoaming is hidden; the platform sets it from telephony.
        NetworkInfo.class.getMethod("setRoaming", boolean.class).invoke(info, true);
        assertThat(tracker.snapshot().get(0).roaming).isTrue();
    }

    // SPEC:run-conditions:R08
    @Test
    @SuppressWarnings("deprecation")
    public void wifiNameBelowApi31_readsWifiManager() {
        connected(ShadowNetwork.newInstance(22), ConnectivityManager.TYPE_WIFI,
                NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        WifiInfo wifiInfo = ShadowWifiInfo.newInstance();
        shadowOf(wifiInfo).setSSID("Home");
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        shadowOf(wifiManager).setConnectionInfo(wifiInfo);

        List<NetworkState> snapshot = tracker.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).kind).isEqualTo(Transport.WIFI);
        assertThat(snapshot.get(0).wifiInfoAvailable).isTrue();
        assertThat(snapshot.get(0).wifiSsid).isEqualTo("\"Home\"");
    }

    // SPEC:run-conditions:R03
    @Test
    @Config(sdk = 24)
    public void onAvailableAlone_notifiesAndSnapshotReadsNetwork() {
        // Before API 26, onCapabilitiesChanged need not follow onAvailable.
        Network wifi = ShadowNetwork.newInstance(23);
        connected(wifi, ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        // At API 24 Robolectric itself calls the callback when a network is added; count from here.
        shadowOf(Looper.getMainLooper()).idle();
        int before = notified;
        shadowOf(cm).getNetworkCallbacks().iterator().next().onAvailable(wifi);
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(notified).isEqualTo(before + 1);
        List<NetworkState> snapshot = tracker.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).validated).isTrue();
        assertThat(snapshot.get(0).metered).isFalse();
    }
}
