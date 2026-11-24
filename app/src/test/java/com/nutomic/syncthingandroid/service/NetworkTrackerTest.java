package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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

    private ConnectivityManager.NetworkCallback callback() {
        Set<ConnectivityManager.NetworkCallback> callbacks = shadowOf(cm).getNetworkCallbacks();
        assertThat(callbacks).hasSize(1);
        return callbacks.iterator().next();
    }

    // SPEC:R05
    @Test
    public void kindOf_mapsTransports() {
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_WIFI))).isEqualTo(Transport.WIFI);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_ETHERNET))).isEqualTo(Transport.ETHERNET);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_CELLULAR))).isEqualTo(Transport.MOBILE);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_BLUETOOTH))).isEqualTo(Transport.MOBILE);
        assertThat(NetworkTracker.kindOf(caps(NetworkCapabilities.TRANSPORT_LOWPAN))).isEqualTo(Transport.OTHER);
    }

    // SPEC:R03
    @Test
    public void start_registersOneCallback_stopUnregisters() {
        tracker.start();
        assertThat(shadowOf(cm).getNetworkCallbacks()).hasSize(1);
        tracker.stop();
        assertThat(shadowOf(cm).getNetworkCallbacks()).isEmpty();
        tracker.stop();
    }

    // SPEC:R03
    @Test
    public void capabilitiesChanged_addsNetworkAndNotifies() {
        tracker.start();
        Network wifi = ShadowNetwork.newInstance(1);
        callback().onCapabilitiesChanged(wifi, caps(NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING));
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
    public void lost_removesNetwork() {
        tracker.start();
        Network mobile = ShadowNetwork.newInstance(2);
        callback().onCapabilitiesChanged(mobile, caps(NetworkCapabilities.TRANSPORT_CELLULAR));
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(tracker.snapshot()).hasSize(1);
        assertThat(tracker.snapshot().get(0).metered).isTrue();
        assertThat(tracker.snapshot().get(0).roaming).isTrue();

        callback().onLost(mobile);
        shadowOf(Looper.getMainLooper()).idle();
        assertThat(tracker.snapshot()).isEmpty();
        assertThat(notified).isEqualTo(2);
    }

    @Test
    public void stop_dropsNetworks() {
        tracker.start();
        callback().onCapabilitiesChanged(ShadowNetwork.newInstance(3), caps(NetworkCapabilities.TRANSPORT_WIFI));
        tracker.stop();
        assertThat(tracker.snapshot()).isEmpty();
    }
}
