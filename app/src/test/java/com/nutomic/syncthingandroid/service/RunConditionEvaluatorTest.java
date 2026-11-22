package com.nutomic.syncthingandroid.service;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Decision;
import com.nutomic.syncthingandroid.service.RunConditionEvaluator.Reason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class RunConditionEvaluatorTest {

    private static final long NOW = 10L * 60 * 60 * 1000; // 10 h since boot
    private static final long MINUTE = 60L * 1000;

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
        // Last run "just now", so the time schedule is not met by default.
        prefs.edit().putLong(Constants.PREF_LAST_RUN_TIME, NOW).commit();
    }

    private static DeviceConditions.Builder device() {
        return new DeviceConditions.Builder()
                .charging(true)
                .powerSaving(false)
                .masterSyncEnabled(true)
                .transport(Transport.NO_ACTIVE_NETWORK);
    }

    private static DeviceConditions wifi() {
        return device().transport(Transport.WIFI).wifiInfoAvailable(true).wifiSsid("\"Home\"").build();
    }

    private Decision decide(DeviceConditions d) {
        return RunConditionEvaluator.decide(prefs, d, NOW, false);
    }

    @Test
    public void forceStart_runsWithNoNetworkAndPowerSave() {
        prefs.edit()
                .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_START)
                .putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER)
                .commit();
        Decision d = decide(device().charging(false).powerSaving(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).containsExactly(Reason.FORCE_START);
    }

    @Test
    public void forceStop_neverRuns() {
        prefs.edit().putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_STOP).commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.FORCE_STOP);
    }

    @Test
    public void timeSchedule_outsideFrame_doesNotRun() {
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
                .putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")
                .putLong(Constants.PREF_LAST_RUN_TIME, NOW - 10 * MINUTE)
                .commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.minutesSinceLastRun).isEqualTo(10);
        assertThat(d.timeConditionMatch).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_WITHIN_TIME_FRAME);
    }

    @Test
    public void timeSchedule_lastRunOlderThanInterval_setsTimeConditionMatch() {
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
                .putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")
                .putLong(Constants.PREF_LAST_RUN_TIME, NOW - 61 * MINUTE)
                .commit();
        Decision d = decide(wifi());
        assertThat(d.timeConditionMatch).isTrue();
        assertThat(d.shouldRun).isTrue();
    }

    @Test
    public void powerSourceChargerOnly_onBattery_doesNotRun() {
        prefs.edit().putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER).commit();
        Decision d = decide(device().charging(false).transport(Transport.WIFI).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_CHARGING);
    }

    @Test
    public void powerSourceBatteryOnly_charging_doesNotRun() {
        prefs.edit().putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.BATTERY).commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_ON_BATTERY_POWER);
    }

    @Test
    public void respectPowerSaving_inPowerSave_doesNotRun() {
        Decision d = decide(device().powerSaving(true).transport(Transport.WIFI).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_WHILE_POWER_SAVING);
    }

    @Test
    public void respectMasterSync_autoSyncOff_doesNotRun() {
        prefs.edit().putBoolean(Constants.PREF_RESPECT_MASTER_SYNC, true).commit();
        Decision d = decide(device().masterSyncEnabled(false).transport(Transport.WIFI).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_WHILE_AUTO_SYNC_DATA_DISABLED);
    }

    @Test
    public void mobileDataAllowed_notRoaming_runs() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true).commit();
        Decision d = decide(device().transport(Transport.MOBILE).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).containsExactly(Reason.ON_MOBILE_DATA, Reason.ON_NONROAMING_MOBILE_DATA).inOrder();
    }

    @Test
    public void mobileDataAllowed_roamingDisallowed_fallsThroughToWifiCheck() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true).commit();
        Decision d = decide(device().transport(Transport.MOBILE).roaming(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(
                Reason.ON_MOBILE_DATA, Reason.NOT_NONROAMING_MOBILE_DATA, Reason.NOT_ON_WIFI).inOrder();
    }

    @Test
    public void globalRoamingDisallowed_onRoamingDataOnly_doesNotRun() {
        // Per-object conditions have no roaming option; they rely on this global gate.
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true)
                .putBoolean(Constants.PREF_RUN_ON_ROAMING, false)
                .commit();
        assertThat(decide(device().transport(Transport.MOBILE).roaming(true).build()).shouldRun).isFalse();
    }

    @Test
    public void wifiAllowed_unmetered_runs() {
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).containsExactly(
                Reason.MOBILE_DATA_DISALLOWED, Reason.ON_WIFI, Reason.ON_NONMETERED_WIFI,
                Reason.ON_WHITELISTED_WIFI).inOrder();
    }

    @Test
    public void wifiAllowed_meteredDisallowed_doesNotRun() {
        Decision d = decide(device().transport(Transport.WIFI).activeNetworkMetered(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.NOT_NONMETERED_WIFI);
    }

    @Test
    public void ethernet_reportedMetered_stillRuns() {
        Decision d = decide(device().transport(Transport.ETHERNET).activeNetworkMetered(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_NONMETERED_WIFI);
    }

    @Test
    public void whitelistOn_ssidListed_runs() {
        prefs.edit()
                .putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true)
                .putStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>(Collections.singleton("\"Home\"")))
                .commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_WHITELISTED_WIFI);
    }

    @Test
    public void whitelistOn_ssidNotListed_doesNotRun() {
        prefs.edit()
                .putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true)
                .putStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>(Collections.singleton("\"Work\"")))
                .commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.NOT_ON_WHITELISTED_WIFI);
    }

    @Test
    public void whitelistOn_ssidUnknown_reasonLocationUnavailable() {
        prefs.edit().putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true).commit();
        for (String ssid : new String[]{null, "<unknown ssid>"}) {
            Decision d = decide(device().transport(Transport.WIFI).wifiInfoAvailable(true).wifiSsid(ssid).build());
            assertThat(d.shouldRun).isFalse();
            assertThat(d.reasons).contains(Reason.LOCATION_UNAVAILABLE);
        }
    }

    @Test
    public void whitelistOn_wifiInfoMissing_doesNotRun() {
        prefs.edit().putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true).commit();
        Decision d = decide(device().transport(Transport.WIFI).wifiInfoAvailable(false).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.NOT_ON_WHITELISTED_WIFI);
    }

    @Test
    public void flightModeAllowed_noActiveNetwork_runs() {
        prefs.edit().putBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, true).commit();
        Decision d = decide(device().transport(Transport.NO_ACTIVE_NETWORK).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).containsExactly(
                Reason.MOBILE_DATA_DISALLOWED, Reason.NOT_ON_WIFI, Reason.ON_FLIGHT_MODE).inOrder();
    }

    @Test
    public void flightModeNotAllowed_noActiveNetwork_doesNotRun() {
        assertThat(decide(device().transport(Transport.NO_ACTIVE_NETWORK).build()).shouldRun).isFalse();
    }

    @Test
    public void noConditionMatched_doesNotRun() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_WIFI, false).commit();
        Decision d = decide(device().transport(Transport.DISCONNECTED).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.MOBILE_DATA_DISALLOWED, Reason.WIFI_DISALLOWED).inOrder();
    }

    @Test
    public void objectConditions_usesObjectPrefNames() {
        String obj = Constants.PREF_OBJECT_PREFIX_FOLDER + "abc";
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_WIFI, false)
                .putBoolean(Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(obj), true)
                .commit();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, wifi(), obj)).isTrue();

        prefs.edit().putBoolean(Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(obj), false).commit();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, wifi(), obj)).isFalse();
    }
}
