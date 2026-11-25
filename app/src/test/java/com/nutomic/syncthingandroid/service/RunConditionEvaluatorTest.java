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
        return device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true)
                .wifiInfoAvailable(true).wifiSsid("\"Home\"").build();
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

    /**
     * Pins the order of the blocking checks: with every one of them failing, each step clears
     * the reported one and the next check in line must be the one reported.
     */
    @Test
    public void blockingChecks_areEvaluatedInOrder() {
        prefs.edit()
                .putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_FORCE_STOP)
                .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
                .putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")
                .putLong(Constants.PREF_LAST_RUN_TIME, NOW - 10 * MINUTE)
                .putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER)
                .putBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true)
                .putBoolean(Constants.PREF_RESPECT_MASTER_SYNC, true)
                .commit();
        DeviceConditions device = device()
                .charging(false).powerSaving(true).masterSyncEnabled(false)
                .transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).build();

        assertThat(decide(device).reasons).containsExactly(Reason.FORCE_STOP);

        prefs.edit().putInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP).commit();
        assertThat(decide(device).reasons).containsExactly(Reason.NOT_WITHIN_TIME_FRAME);

        prefs.edit().putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false).commit();
        assertThat(decide(device).reasons).containsExactly(Reason.NOT_CHARGING);

        prefs.edit().putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER_BATTERY).commit();
        assertThat(decide(device).reasons).containsExactly(Reason.NOT_WHILE_POWER_SAVING);

        prefs.edit().putBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, false).commit();
        assertThat(decide(device).reasons).containsExactly(Reason.NOT_WHILE_AUTO_SYNC_DATA_DISABLED);

        prefs.edit().putBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false).commit();
        Decision last = decide(device);
        assertThat(last.shouldRun).isTrue();
        assertThat(last.reasons).contains(Reason.ON_WIFI);
    }

    /** Wi-Fi is checked before flight mode, so a Wi-Fi match never reports flight mode. */
    @Test
    public void wifiMatch_winsOverFlightMode() {
        prefs.edit().putBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, true).commit();
        Decision d = decide(wifi());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).doesNotContain(Reason.ON_FLIGHT_MODE);
    }

    @Test
    public void powerSourceChargerOnly_onBattery_doesNotRun() {
        prefs.edit().putString(Constants.PREF_POWER_SOURCE, Constants.PowerSource.CHARGER).commit();
        Decision d = decide(device().charging(false).transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).build());
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
        Decision d = decide(device().powerSaving(true).transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.NOT_WHILE_POWER_SAVING);
    }

    @Test
    public void respectMasterSync_autoSyncOff_doesNotRun() {
        prefs.edit().putBoolean(Constants.PREF_RESPECT_MASTER_SYNC, true).commit();
        Decision d = decide(device().masterSyncEnabled(false).transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).build());
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
        Decision d = decide(device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).wifiMetered(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.NOT_NONMETERED_WIFI);
    }

    @Test
    public void ethernet_reportedMetered_stillRuns() {
        Decision d = decide(device().transport(Transport.ETHERNET).ethernetConnected(true).wifiMetered(true).build());
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
            Decision d = decide(device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).wifiInfoAvailable(true).wifiSsid(ssid).build());
            assertThat(d.shouldRun).isFalse();
            assertThat(d.reasons).contains(Reason.LOCATION_UNAVAILABLE);
        }
    }

    @Test
    public void whitelistOn_wifiInfoMissing_doesNotRun() {
        prefs.edit().putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true).commit();
        Decision d = decide(device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true).wifiInfoAvailable(false).build());
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
        Decision d = decide(device().transport(Transport.NO_ACTIVE_NETWORK).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.MOBILE_DATA_DISALLOWED, Reason.WIFI_DISALLOWED).inOrder();
    }

    private int sleepIntervalPref() {
        return RunConditionEvaluator.minutesPref(prefs, Constants.PREF_SLEEP_INTERVAL_MINUTES,
                RunConditionEvaluator.SLEEP_INTERVAL_DEFAULT, 1, RunConditionEvaluator.SLEEP_INTERVAL_MAX);
    }

    @Test
    public void minutesPref_validValue_returned() {
        prefs.edit().putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "15").commit();
        assertThat(sleepIntervalPref()).isEqualTo(15);
    }

    @Test
    public void minutesPref_nonNumeric_returnsDefault() {
        for (String bad : new String[]{"abc", ""}) {
            prefs.edit().putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, bad).commit();
            assertThat(sleepIntervalPref()).isEqualTo(60);
        }
    }

    @Test
    public void minutesPref_outOfRange_returnsDefault() {
        for (String bad : new String[]{"0", "-5", "30241"}) {
            prefs.edit().putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, bad).commit();
            assertThat(sleepIntervalPref()).isEqualTo(60);
        }
    }

    @Test
    public void minutesPref_missing_returnsDefault() {
        assertThat(sleepIntervalPref()).isEqualTo(60);
        assertThat(RunConditionEvaluator.minutesPref(prefs, Constants.PREF_SYNC_DURATION_MINUTES,
                RunConditionEvaluator.SYNC_DURATION_DEFAULT, 1, RunConditionEvaluator.SYNC_DURATION_MAX))
                .isEqualTo(5);
    }

    @Test
    public void decide_nonNumericSleepInterval_usesDefault() {
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, true)
                .putLong(Constants.PREF_LAST_RUN_TIME, NOW - 10 * MINUTE)
                .putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")
                .commit();
        Decision expected = decide(wifi());

        prefs.edit().putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "abc").commit();
        Decision actual = decide(wifi());

        assertThat(actual.shouldRun).isEqualTo(expected.shouldRun);
        assertThat(actual.reasons).isEqualTo(expected.reasons);
        assertThat(actual.timeConditionMatch).isEqualTo(expected.timeConditionMatch);
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

    /** A Wi-Fi that Android has not confirmed reaches the internet. */
    private static DeviceConditions.Builder unvalidatedWifi() {
        return device().wifiConnected(true).wifiValidated(false).wifiInfoAvailable(true).wifiSsid("\"Lan\"");
    }

    // SPEC:R07
    @Test
    public void validatedWifi_runsUnderWifiOnly() {
        Decision d = decide(device().transport(Transport.MOBILE).mobileConnected(true)
                .wifiConnected(true).wifiValidated(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_WIFI);
    }

    // SPEC:R07
    @Test
    public void unvalidatedWifi_mobileAllowed_runs() {
        prefs.edit().putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true).commit();
        // Mobile data is connected but not the default network, so only the Wi-Fi route can say yes.
        Decision d = decide(unvalidatedWifi().transport(Transport.WIFI).mobileConnected(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_WIFI);
    }

    // SPEC:R07
    @Test
    public void unvalidatedWifi_roamingDisallowed_doesNotRun() {
        // Abroad: captive Wi-Fi plus roaming mobile data, which carries the traffic.
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true)
                .putBoolean(Constants.PREF_RUN_ON_ROAMING, false)
                .commit();
        Decision d = decide(unvalidatedWifi().transport(Transport.MOBILE).mobileConnected(true).roaming(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.WIFI_WITHOUT_INTERNET);

        String obj = Constants.PREF_OBJECT_PREFIX_FOLDER + "abc";
        prefs.edit().putBoolean(Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(obj), true).commit();
        DeviceConditions onWifiRoute = unvalidatedWifi().transport(Transport.WIFI).mobileConnected(true).roaming(true).build();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, onWifiRoute, obj)).isFalse();
    }

    // SPEC:R07
    @Test
    public void unvalidatedWifi_noMobileNetwork_runs() {
        Decision d = decide(unvalidatedWifi().transport(Transport.WIFI).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_WIFI);
    }

    // SPEC:R07
    // SPEC:R10
    @Test
    public void unvalidatedWifi_mobileOnButDisallowed_doesNotRun() {
        Decision d = decide(unvalidatedWifi().transport(Transport.MOBILE).mobileConnected(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).containsExactly(Reason.MOBILE_DATA_DISALLOWED, Reason.WIFI_WITHOUT_INTERNET).inOrder();
    }

    // SPEC:R07
    @Test
    public void ethernet_countsAsUnmeteredWifi() {
        Decision d = decide(device().transport(Transport.ETHERNET).ethernetConnected(true)
                .wifiConnected(true).wifiValidated(true).wifiMetered(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_NONMETERED_WIFI);
    }

    // SPEC:R07
    @Test
    public void mobileDefault_countsAsMobile() {
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, true)
                .putBoolean(Constants.PREF_RUN_ON_WIFI, false)
                .commit();
        Decision d = decide(device().transport(Transport.MOBILE).mobileConnected(true).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_MOBILE_DATA);

        Decision onWifi = decide(device().transport(Transport.WIFI).mobileConnected(true)
                .wifiConnected(true).wifiValidated(true).build());
        assertThat(onWifi.shouldRun).isFalse();
    }

    // SPEC:R07
    @Test
    public void objectConditions_unvalidatedWifi_followObjectMobilePref() {
        String obj = Constants.PREF_OBJECT_PREFIX_FOLDER + "abc";
        prefs.edit()
                .putBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, false)
                .putBoolean(Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(obj), true)
                .commit();
        // A roaming-free mobile network that is not the default: the mobile route alone does not decide.
        DeviceConditions d = unvalidatedWifi().transport(Transport.WIFI).mobileConnected(true).build();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, d, obj)).isTrue();
        assertThat(decide(d).shouldRun).isFalse();
        assertThat(decide(d).reasons).contains(Reason.WIFI_WITHOUT_INTERNET);
    }

    // SPEC:R08
    @Test
    public void unmeteredWifi_meteredDefault_runsUnderUnmeteredOnly() {
        // A VPN or mobile data carries traffic and is metered; the Wi-Fi itself is not.
        Decision d = decide(device().transport(Transport.MOBILE).mobileConnected(true)
                .wifiConnected(true).wifiValidated(true).wifiMetered(false).build());
        assertThat(d.shouldRun).isTrue();
        assertThat(d.reasons).contains(Reason.ON_NONMETERED_WIFI);
    }

    // SPEC:R08
    @Test
    public void meteredWifi_doesNotRun() {
        Decision d = decide(device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true)
                .wifiMetered(true).build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.NOT_NONMETERED_WIFI);
    }

    // SPEC:R08
    @Test
    public void whitelistedSsid_runs() {
        prefs.edit()
                .putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true)
                .putStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>(Collections.singleton("\"Home\"")))
                .commit();
        Decision d = decide(device().transport(Transport.MOBILE).mobileConnected(true)
                .wifiConnected(true).wifiValidated(true).wifiInfoAvailable(true).wifiSsid("\"Home\"").build());
        assertThat(d.shouldRun).isTrue();
    }

    // SPEC:R08
    @Test
    public void hiddenSsid_givesLocationReason() {
        prefs.edit().putBoolean(Constants.PREF_USE_WIFI_SSID_WHITELIST, true).commit();
        Decision d = decide(device().transport(Transport.WIFI).wifiConnected(true).wifiValidated(true)
                .wifiInfoAvailable(true).wifiSsid("<unknown ssid>").build());
        assertThat(d.shouldRun).isFalse();
        assertThat(d.reasons).contains(Reason.LOCATION_UNAVAILABLE);
        assertThat(d.reasons).doesNotContain(Reason.NOT_ON_WHITELISTED_WIFI);
    }

    /** R07's table, row by row, for a folder's own conditions. */
    // SPEC:R07
    @Test
    public void objectConditions_followEachR07Row() {
        String obj = Constants.PREF_OBJECT_PREFIX_FOLDER + "rows";
        String objMobile = Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(obj);

        // Row 1: a validated Wi-Fi counts, with mobile data connected and not allowed.
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, device().transport(Transport.WIFI)
                .wifiConnected(true).wifiValidated(true).mobileConnected(true).build(), obj)).isTrue();

        // Row 2: a Wi-Fi without internet counts when no mobile network is connected.
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs,
                unvalidatedWifi().transport(Transport.WIFI).build(), obj)).isTrue();

        // Row 3: it does not count when mobile data is connected and not allowed for the folder.
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs,
                unvalidatedWifi().transport(Transport.MOBILE).mobileConnected(true).build(), obj)).isFalse();

        // Row 4: Ethernet counts as unmetered Wi-Fi, even beside a metered Wi-Fi.
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, device().transport(Transport.ETHERNET)
                .ethernetConnected(true).wifiConnected(true).wifiValidated(true).wifiMetered(true).build(), obj)).isTrue();

        // Row 5: a default mobile network counts as mobile data, so only the folder's mobile setting lets it run.
        DeviceConditions mobileOnly = device().transport(Transport.MOBILE).mobileConnected(true).build();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, mobileOnly, obj)).isFalse();
        prefs.edit().putBoolean(objMobile, true).commit();
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs, mobileOnly, obj)).isTrue();
        prefs.edit().putBoolean(objMobile, false).commit();

        // Row 6: no physical network; folders have no flight-mode setting of their own.
        assertThat(RunConditionEvaluator.checkObjectConditions(prefs,
                device().transport(Transport.NO_ACTIVE_NETWORK).build(), obj)).isFalse();
    }
}
