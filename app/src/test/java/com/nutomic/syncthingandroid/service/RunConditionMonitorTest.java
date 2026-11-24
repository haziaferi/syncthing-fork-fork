package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Looper;
import android.os.PowerManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class RunConditionMonitorTest {

    private static final Network WIFI = ShadowNetwork.newInstance(11);
    private static final Network MOBILE = ShadowNetwork.newInstance(12);

    private Context context;
    private SharedPreferences prefs;
    private ConnectivityManager cm;
    private final List<Boolean> decisions = new ArrayList<>();
    private RunConditionMonitor monitor;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
        cm = context.getSystemService(ConnectivityManager.class);
        shadowOf(cm).clearAllNetworks();
        monitor = new RunConditionMonitor(context, prefs, decisions::add, m -> { });
        idle();
    }

    @After
    public void tearDown() {
        monitor.shutdown();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS);
    }

    private ConnectivityManager.NetworkCallback callback() {
        Set<ConnectivityManager.NetworkCallback> callbacks = shadowOf(cm).getNetworkCallbacks();
        assertThat(callbacks).hasSize(1);
        return callbacks.iterator().next();
    }

    /** Connects a network the way a device does: Android knows it, then the callback reports it. */
    @SuppressWarnings("deprecation")
    private void up(Network network, int transport, int legacyType, int... capabilities) {
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addTransportType(transport);
        for (int c : capabilities) {
            shadowOf(caps).addCapability(c);
        }
        shadowOf(cm).addNetwork(network, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                legacyType, 0, true, NetworkInfo.State.CONNECTED));
        shadowOf(cm).setNetworkCapabilities(network, caps);
        callback().onCapabilitiesChanged(network, caps);
        idle();
    }

    private void wifiUp() {
        up(WIFI, NetworkCapabilities.TRANSPORT_WIFI, ConnectivityManager.TYPE_WIFI,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
    }

    private void mobileUp() {
        up(MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR, ConnectivityManager.TYPE_MOBILE,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
    }

    private void wifiLost() {
        shadowOf(cm).removeNetwork(WIFI);
        callback().onLost(WIFI);
        idle();
    }

    private void powerSave(boolean on) {
        shadowOf(context.getSystemService(PowerManager.class)).setIsPowerSaveMode(on);
        monitor.updateShouldRunDecision();
        idle();
    }

    private void forceStopSilently() {
        prefs.edit().putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_STOP).commit();
    }

    // SPEC:R03
    @Test
    public void wifiAppears_decisionTurnsTrue() {
        assertThat(decisions).isEmpty();
        wifiUp();
        assertThat(decisions).containsExactly(true);
    }

    // SPEC:R03
    @Test
    public void shutdown_unregistersCallback() {
        wifiUp();
        monitor.shutdown();
        assertThat(shadowOf(cm).getNetworkCallbacks()).isEmpty();
        forceStopSilently();
        idleFor(TimeUnit.MINUTES.toMillis(20));
        assertThat(decisions).containsExactly(true);
    }

    // SPEC:R04
    @Test
    public void recheck_after15Minutes() {
        wifiUp();
        forceStopSilently();
        idleFor(TimeUnit.MINUTES.toMillis(14));
        assertThat(decisions).containsExactly(true);
        idleFor(TimeUnit.MINUTES.toMillis(1));
        assertThat(decisions).containsExactly(true, false).inOrder();
    }

    // SPEC:R04
    @Test
    public void screenOn_rechecks() {
        wifiUp();
        forceStopSilently();
        context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        idle();
        assertThat(decisions).containsExactly(true, false).inOrder();
    }

    // SPEC:R09
    @Test
    public void wifiDrop5s_keepsRunning() {
        wifiUp();
        wifiLost();
        idleFor(5_000);
        wifiUp();
        idleFor(30_000);
        assertThat(decisions).containsExactly(true);
    }

    // SPEC:R09
    @Test
    public void wifiDrop11s_stops() {
        wifiUp();
        wifiLost();
        idleFor(9_000);
        assertThat(decisions).containsExactly(true);
        idleFor(2_000);
        assertThat(decisions).containsExactly(true, false).inOrder();
    }

    // SPEC:R09
    @Test
    public void forceStop_actsAtOnce() {
        wifiUp();
        forceStopSilently();
        monitor.updateShouldRunDecision();
        assertThat(decisions).containsExactly(true, false).inOrder();
    }

    // SPEC:R09
    @Test
    public void objectConditions_heldDuringShortDrop() {
        String folder = Constants.PREF_OBJECT_PREFIX_FOLDER + "abc";
        wifiUp();
        assertThat(monitor.checkObjectSyncConditions(folder)).isTrue();
        wifiLost();
        assertThat(monitor.checkObjectSyncConditions(folder)).isTrue();
        idleFor(11_000);
        assertThat(monitor.checkObjectSyncConditions(folder)).isFalse();
    }

    // SPEC:R09
    @Test
    public void objectHold_notCarriedOverAStop() {
        // App runs on Wi-Fi or mobile data; the folder on Wi-Fi only.
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true).commit();
        String folder = Constants.PREF_OBJECT_PREFIX_FOLDER + "abc";
        wifiUp();
        assertThat(monitor.checkObjectSyncConditions(folder)).isTrue();

        powerSave(true);
        idleFor(11_000);
        assertThat(decisions).containsExactly(true, false).inOrder();

        wifiLost();
        mobileUp();
        powerSave(false);
        assertThat(decisions).containsExactly(true, false, true).inOrder();
        assertThat(monitor.checkObjectSyncConditions(folder)).isFalse();
    }

    // SPEC:R09
    @Test
    public void timeWindowEnd_stopsAtOnce() {
        wifiUp();
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
                .putLong(Constants.PREF_LAST_RUN_TIME, android.os.SystemClock.elapsedRealtime())
                .commit();
        Intent windowEnd = new Intent(RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED)
                .putExtra(RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW, false);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(windowEnd);
        idle();
        assertThat(decisions).containsExactly(true, false).inOrder();
    }

    // SPEC:R04
    @Test
    public void screenOn_catchesMissedWifiLoss() {
        wifiUp();
        // The vendor drops the onLost callback: Android no longer has the Wi-Fi.
        shadowOf(cm).removeNetwork(WIFI);
        context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
        idle();
        idleFor(11_000);
        assertThat(decisions).containsExactly(true, false).inOrder();
    }
}
