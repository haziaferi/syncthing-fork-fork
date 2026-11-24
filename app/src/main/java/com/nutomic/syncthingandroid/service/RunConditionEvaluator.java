package com.nutomic.syncthingandroid.service;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.StringRes;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.DeviceConditions.Transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether Syncthing should run, from the user's run-condition prefs and a
 * {@link DeviceConditions} snapshot. Holds no Android framework state: the caller passes
 * the time and the device state in, which keeps the decision testable.
 */
public final class RunConditionEvaluator {

    private static final String TAG = "RunConditionEvaluator";

    /** Defaults and ranges match the input checks in SettingsRunConditionsScreen. */
    public static final int SYNC_DURATION_DEFAULT = 5;
    public static final int SYNC_DURATION_MAX = 1440;
    public static final int SLEEP_INTERVAL_DEFAULT = 60;
    public static final int SLEEP_INTERVAL_MAX = 30240;

    private RunConditionEvaluator() {
    }

    /**
     * Reads a minutes pref stored as a string. A missing, non-numeric or out-of-range value
     * (for example from an imported backup) falls back to {@code defaultMinutes}.
     */
    public static int minutesPref(SharedPreferences prefs, String key, int defaultMinutes, int min, int max) {
        String value = prefs.getString(key, null);
        if (value == null) {
            return defaultMinutes;
        }
        try {
            int minutes = Integer.parseInt(value.trim());
            if (minutes >= min && minutes <= max) {
                return minutes;
            }
        } catch (NumberFormatException ignored) {
            // Falls through to the default below.
        }
        Log.w(TAG, "Invalid value for pref \"" + key + "\", using default " + defaultMinutes);
        return defaultMinutes;
    }

    public static int sleepIntervalMinutes(SharedPreferences prefs) {
        return minutesPref(prefs, Constants.PREF_SLEEP_INTERVAL_MINUTES, SLEEP_INTERVAL_DEFAULT, 1, SLEEP_INTERVAL_MAX);
    }

    public static int syncDurationMinutes(SharedPreferences prefs) {
        return minutesPref(prefs, Constants.PREF_SYNC_DURATION_MINUTES, SYNC_DURATION_DEFAULT, 1, SYNC_DURATION_MAX);
    }

    /**
     * One line of the run-decision explanation shown in the app.
     * {@link #newlineBefore} mirrors the "\n" prefixes of the original explanation text.
     */
    public enum Reason {
        FORCE_START(R.string.reason_force_start, false),
        FORCE_STOP(R.string.reason_force_stop, false),
        /** Formatted with {@link Decision#minutesSinceLastRun}. */
        NOT_WITHIN_TIME_FRAME(R.string.reason_not_within_time_frame_2, false),
        NOT_CHARGING(R.string.reason_not_charging, false),
        NOT_ON_BATTERY_POWER(R.string.reason_not_on_battery_power, false),
        NOT_WHILE_POWER_SAVING(R.string.reason_not_while_power_saving, false),
        NOT_WHILE_AUTO_SYNC_DATA_DISABLED(R.string.reason_not_while_auto_sync_data_disabled, false),
        MOBILE_DATA_DISALLOWED(R.string.reason_mobile_data_disallowed, false),
        ON_MOBILE_DATA(R.string.reason_on_mobile_data, false),
        NOT_ON_MOBILE_DATA(R.string.reason_not_on_mobile_data, false),
        ON_ROAMING_NONROAMING_MOBILE_DATA(R.string.reason_on_roaming_nonroaming_mobile_data, true),
        ON_NONROAMING_MOBILE_DATA(R.string.reason_on_nonroaming_mobile_data, true),
        NOT_NONROAMING_MOBILE_DATA(R.string.reason_not_nonroaming_mobile_data, true),
        WIFI_DISALLOWED(R.string.reason_wifi_disallowed, true),
        ON_WIFI(R.string.reason_on_wifi, true),
        NOT_ON_WIFI(R.string.reason_not_on_wifi, true),
        WIFI_WITHOUT_INTERNET(R.string.reason_wifi_without_internet, true),
        ON_METERED_NONMETERED_WIFI(R.string.reason_on_metered_nonmetered_wifi, true),
        ON_NONMETERED_WIFI(R.string.reason_on_nonmetered_wifi, true),
        NOT_NONMETERED_WIFI(R.string.reason_not_nonmetered_wifi, true),
        ON_WHITELISTED_WIFI(R.string.reason_on_whitelisted_wifi, true),
        NOT_ON_WHITELISTED_WIFI(R.string.reason_not_on_whitelisted_wifi, true),
        LOCATION_UNAVAILABLE(R.string.reason_location_unavailable, true),
        ON_FLIGHT_MODE(R.string.reason_on_flight_mode, true);

        @StringRes public final int stringRes;
        public final boolean newlineBefore;

        Reason(@StringRes int stringRes, boolean newlineBefore) {
            this.stringRes = stringRes;
            this.newlineBefore = newlineBefore;
        }
    }

    public static final class Decision {
        public final boolean shouldRun;
        /** Explanation lines, in the order they are shown. */
        public final List<Reason> reasons;
        /** Updated "within a run time frame" state for the time schedule. */
        public final boolean timeConditionMatch;
        /** Minutes since the last run; only meaningful with {@link Reason#NOT_WITHIN_TIME_FRAME}. */
        public final int minutesSinceLastRun;

        public Decision(boolean shouldRun, List<Reason> reasons, boolean timeConditionMatch, int minutesSinceLastRun) {
            this.shouldRun = shouldRun;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.timeConditionMatch = timeConditionMatch;
            this.minutesSinceLastRun = minutesSinceLastRun;
        }
    }

    /** Result of one condition check; reason is null when the check adds no explanation line. */
    private static final class Check {
        final boolean met;
        final Reason reason;

        Check(boolean met, Reason reason) {
            this.met = met;
            this.reason = reason;
        }
    }

    /**
     * Determines if Syncthing should currently run.
     *
     * @param timeConditionMatch whether we are currently inside a time-schedule run frame
     */
    public static Decision decide(SharedPreferences prefs, DeviceConditions device,
                                  long nowElapsedMs, boolean timeConditionMatch) {
        List<Reason> reasons = new ArrayList<>();

        int prefBtnStateForceStartStop = prefs.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP);
        boolean prefRespectPowerSaving = prefs.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = prefs.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);
        boolean prefRunInFlightMode = prefs.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);
        boolean prefRunOnTimeSchedule = prefs.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);

        switch (prefBtnStateForceStartStop) {
            case Constants.BTNSTATE_FORCE_START:
                reasons.add(Reason.FORCE_START);
                return new Decision(true, reasons, timeConditionMatch, 0);
            case Constants.BTNSTATE_FORCE_STOP:
                reasons.add(Reason.FORCE_STOP);
                return new Decision(false, reasons, timeConditionMatch, 0);
        }

        // Time schedule: we are inside a run frame if the last run was more than one sleep interval ago.
        long lastRunTime = prefs.getLong(Constants.PREF_LAST_RUN_TIME, 0);
        if (nowElapsedMs - lastRunTime > sleepIntervalMinutes(prefs) * 60 * 1000) {
            timeConditionMatch = true;
        }
        if (prefRunOnTimeSchedule && !timeConditionMatch) {
            int minutes = (int) (nowElapsedMs - lastRunTime) / (60 * 1000);
            reasons.add(Reason.NOT_WITHIN_TIME_FRAME);
            return new Decision(false, reasons, timeConditionMatch, minutes);
        }

        Check check = checkPowerSource(prefs, device, Constants.PREF_POWER_SOURCE);
        if (!check.met) {
            reasons.add(check.reason);
            return new Decision(false, reasons, timeConditionMatch, 0);
        }

        if (prefRespectPowerSaving && device.powerSaving) {
            reasons.add(Reason.NOT_WHILE_POWER_SAVING);
            return new Decision(false, reasons, timeConditionMatch, 0);
        }

        if (prefRespectMasterSync && !device.masterSyncEnabled) {
            reasons.add(Reason.NOT_WHILE_AUTO_SYNC_DATA_DISABLED);
            return new Decision(false, reasons, timeConditionMatch, 0);
        }

        check = checkMobileData(prefs, device, Constants.PREF_RUN_ON_MOBILE_DATA);
        reasons.add(check.reason);
        if (check.met) {
            check = checkRoaming(prefs, device, Constants.PREF_RUN_ON_ROAMING);
            reasons.add(check.reason);
            if (check.met) {
                return new Decision(true, reasons, timeConditionMatch, 0);
            }
        }

        check = checkWifi(prefs, device, Constants.PREF_RUN_ON_WIFI, Constants.PREF_RUN_ON_MOBILE_DATA);
        reasons.add(check.reason);
        if (check.met) {
            check = checkMeteredWifi(prefs, device, Constants.PREF_RUN_ON_METERED_WIFI);
            reasons.add(check.reason);
            if (check.met) {
                check = checkWhitelistedWifi(prefs, device,
                        Constants.PREF_USE_WIFI_SSID_WHITELIST, Constants.PREF_WIFI_SSID_WHITELIST);
                reasons.add(check.reason);
                if (check.met) {
                    return new Decision(true, reasons, timeConditionMatch, 0);
                }
            }
        }

        if (prefRunInFlightMode && device.transport == Transport.NO_ACTIVE_NETWORK) {
            reasons.add(Reason.ON_FLIGHT_MODE);
            return new Decision(true, reasons, timeConditionMatch, 0);
        }

        return new Decision(false, reasons, timeConditionMatch, 0);
    }

    /**
     * Checks an object's individual sync conditions.
     * Precondition: the object has "...CustomSyncConditionsEnabled == true".
     */
    public static boolean checkObjectConditions(SharedPreferences prefs, DeviceConditions device,
                                                String objectPrefixAndId) {
        if (!checkPowerSource(prefs, device, Constants.DYN_PREF_OBJECT_SYNC_ON_POWER_SOURCE(objectPrefixAndId)).met) {
            return false;
        }
        if (checkMobileData(prefs, device, Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(objectPrefixAndId)).met) {
            return true;
        }
        return checkWifi(prefs, device, Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(objectPrefixAndId),
                        Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(objectPrefixAndId)).met
                && checkMeteredWifi(prefs, device, Constants.DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(objectPrefixAndId)).met
                && checkWhitelistedWifi(prefs, device,
                        Constants.DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(objectPrefixAndId),
                        Constants.DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(objectPrefixAndId)).met;
    }

    /**
     * Whether the phone is on Wi-Fi for these conditions (spec R07). Ethernet counts as Wi-Fi.
     * A Wi-Fi without internet counts only if mobile data is allowed by the same conditions
     * ({@code mobilePrefName}) or no mobile network is connected: otherwise Android would send
     * Syncthing's internet traffic over mobile data.
     */
    private static Check checkWifi(SharedPreferences prefs, DeviceConditions device, String prefName,
                                   String mobilePrefName) {
        if (!prefs.getBoolean(prefName, true)) {
            return new Check(false, Reason.WIFI_DISALLOWED);
        }
        if (device.ethernetConnected) {
            return new Check(true, Reason.ON_WIFI);
        }
        if (device.wifiConnected) {
            if (device.wifiValidated || prefs.getBoolean(mobilePrefName, false) || !device.mobileConnected) {
                return new Check(true, Reason.ON_WIFI);
            }
            return new Check(false, Reason.WIFI_WITHOUT_INTERNET);
        }
        // Not returning early on purpose: the flight-mode check in decide() still runs.
        return new Check(false, Reason.NOT_ON_WIFI);
    }

    private static Check checkPowerSource(SharedPreferences prefs, DeviceConditions device, String prefName) {
        switch (prefs.getString(prefName, Constants.PowerSource.CHARGER_BATTERY)) {
            case Constants.PowerSource.CHARGER:
                if (!device.charging) {
                    return new Check(false, Reason.NOT_CHARGING);
                }
                break;
            case Constants.PowerSource.BATTERY:
                if (device.charging) {
                    return new Check(false, Reason.NOT_ON_BATTERY_POWER);
                }
                break;
            case Constants.PowerSource.CHARGER_BATTERY:
            default:
                break;
        }
        return new Check(true, null);
    }

    private static Check checkWhitelistedWifi(SharedPreferences prefs, DeviceConditions device,
                                              String prefNameUseWhitelist, String prefNameSelectedSsids) {
        boolean whitelistEnabled = prefs.getBoolean(prefNameUseWhitelist, false);
        Set<String> whitelistedSsids = prefs.getStringSet(prefNameSelectedSsids, new HashSet<>());
        if (!whitelistEnabled) {
            return new Check(true, Reason.ON_WHITELISTED_WIFI);
        }
        if (!device.wifiInfoAvailable) {
            // Wi-Fi may have been turned off in the meantime.
            return new Check(false, Reason.NOT_ON_WHITELISTED_WIFI);
        }
        String ssid = device.wifiSsid;
        if (ssid == null || ssid.equals("<unknown ssid>")) {
            // Android hides the SSID without location access.
            return new Check(false, Reason.LOCATION_UNAVAILABLE);
        }
        if (whitelistedSsids.contains(ssid)) {
            return new Check(true, Reason.ON_WHITELISTED_WIFI);
        }
        return new Check(false, Reason.NOT_ON_WHITELISTED_WIFI);
    }

    private static Check checkMeteredWifi(SharedPreferences prefs, DeviceConditions device, String prefName) {
        if (prefs.getBoolean(prefName, false)) {
            // Both metered and non-metered Wi-Fi are allowed.
            return new Check(true, Reason.ON_METERED_NONMETERED_WIFI);
        }
        if (!isMetered(device)) {
            return new Check(true, Reason.ON_NONMETERED_WIFI);
        }
        return new Check(false, Reason.NOT_NONMETERED_WIFI);
    }

    private static Check checkMobileData(SharedPreferences prefs, DeviceConditions device, String prefName) {
        if (!prefs.getBoolean(prefName, false)) {
            return new Check(false, Reason.MOBILE_DATA_DISALLOWED);
        }
        if (device.transport == Transport.MOBILE) {
            return new Check(true, Reason.ON_MOBILE_DATA);
        }
        return new Check(false, Reason.NOT_ON_MOBILE_DATA);
    }

    private static Check checkRoaming(SharedPreferences prefs, DeviceConditions device, String prefName) {
        if (prefs.getBoolean(prefName, false)) {
            // Both roaming and non-roaming mobile data are allowed.
            return new Check(true, Reason.ON_ROAMING_NONROAMING_MOBILE_DATA);
        }
        if (!(device.isConnected() && device.roaming)) {
            return new Check(true, Reason.ON_NONROAMING_MOBILE_DATA);
        }
        return new Check(false, Reason.NOT_NONROAMING_MOBILE_DATA);
    }

    /**
     * The Wi-Fi's own metered state, not the default network's (spec R08): a VPN or mobile
     * data carrying the traffic says nothing about the Wi-Fi. Ethernet is treated as unmetered,
     * to allow syncing on Android TV and emulators.
     */
    private static boolean isMetered(DeviceConditions device) {
        if (device.ethernetConnected || !device.wifiConnected) {
            return false;
        }
        return device.wifiMetered;
    }
}
