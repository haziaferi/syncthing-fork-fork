package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.SystemClock;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.util.JobUtils;

import javax.inject.Inject;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents.
 */
public class RunConditionMonitor {

    private static final String TAG = "RunConditionMonitor";

    private Boolean ENABLE_VERBOSE_LOG = false;

    public static final String ACTION_SYNC_TRIGGER_FIRED =
        ".service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED";

    public static final String ACTION_UPDATE_SHOULDRUN_DECISION =
        ".service.RunConditionMonitor.ACTION_UPDATE_SHOULDRUN_DECISION";

    public static final String EXTRA_BEGIN_ACTIVE_TIME_WINDOW =
        ".service.RunConditionMonitor.BEGIN_ACTIVE_TIME_WINDOW";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable updateShouldRunDecisionRunnable = new Runnable() {
                @Override
                public void run() {
                    updateShouldRunDecision();
                }
            };
            mainLooper.post(updateShouldRunDecisionRunnable);
        }
    };

    public interface OnShouldRunChangedListener {
        void onShouldRunDecisionChanged(boolean shouldRun);
    }

    public interface OnSyncPreconditionChangedListener {
        void onSyncPreconditionChanged(RunConditionMonitor runConditionMonitor);
    }

    private final Context mContext;
    private ReceiverManager mReceiverManager;
    private @Nullable SyncTriggerReceiver mSyncTriggerReceiver = null;
    private @Nullable UpdateShouldRunDecisionReceiver mUpdateShouldRunDecisionReceiver = null;
    private Resources res;
    private String mRunDecisionExplanation = "";

    /**
     * Only relevant if the user has enabled turning Syncthing on by
     * time schedule for a specific amount of time periodically.
     * Holds true if we are within a "SyncthingNative should run" time frame.
     * Initial status false because we check if the last sync was more than one hour ago on app start.
     */
    private Boolean mTimeConditionMatch = false;
    // Avoid re-scheduling start if run conditions change while already running.
    private Boolean mRunAllowedStopScheduled = false;

    private int triggeredSyncDurationS = 10;
    private int triggeredSyncSleepIntervalS = 10;

    @Inject
    SharedPreferences mPreferences;

    /**
     * Sending callback notifications through OnShouldRunChangedListener is enabled if not null.
     */
    private @Nullable OnShouldRunChangedListener mOnShouldRunChangedListener = null;

    /**
     * Sending callback notifications through OnSyncPreconditionChangedListener is enabled if not null.
     */
    private @Nullable OnSyncPreconditionChangedListener mOnSyncPreconditionChangedListener = null;

    /**
     * Stores the result of the last call to {@link #decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public RunConditionMonitor(Context context,
            OnShouldRunChangedListener onShouldRunChangedListener,
            OnSyncPreconditionChangedListener onSyncPreconditionChangedListener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("Created new instance");
        mContext = context;
        res = mContext.getResources();
        mOnShouldRunChangedListener = onShouldRunChangedListener;
        mOnSyncPreconditionChangedListener = onSyncPreconditionChangedListener;

        /**
         * Register broadcast receivers.
         */
        // NetworkReceiver
        ReceiverManager.registerReceiver(mContext, new NetworkReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // BatteryReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ReceiverManager.registerReceiver(mContext, new BatteryReceiver(), filter);

        // PowerSaveModeChangedReceiver
        ReceiverManager.registerReceiver(mContext,
                new PowerSaveModeChangedReceiver(),
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));

        // SyncStatusObserver to monitor android's "AutoSync" quick toggle.
        mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);

        // SyncTriggerReceiver
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mSyncTriggerReceiver = new SyncTriggerReceiver();
        localBroadcastManager.registerReceiver(mSyncTriggerReceiver,
                new IntentFilter(ACTION_SYNC_TRIGGER_FIRED));

        // UpdateShouldRunDecisionReceiver
        mUpdateShouldRunDecisionReceiver = new UpdateShouldRunDecisionReceiver();
        localBroadcastManager.registerReceiver(mUpdateShouldRunDecisionReceiver,
                new IntentFilter(ACTION_UPDATE_SHOULDRUN_DECISION));

        if (!Constants.isRunningOnEmulator()) {
            triggeredSyncSleepIntervalS = RunConditionEvaluator.sleepIntervalMinutes(mPreferences) * 60;
        }
        long lastSyncTimeSinceBootMillisecs = mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0);
        long elapsedRealtime = SystemClock.elapsedRealtime();
        /**
         * after a reboot lastSyncTimeSinceBootMillisecs might be larger than elapsedRealtime,
         * since it is referring to the previous reboot
         * in this case we set mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0)
         * to -triggeredSyncSleepIntervalS, so mTimeConditionMatch is guaranteed to be true
         */
        if (lastSyncTimeSinceBootMillisecs > elapsedRealtime) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putLong(Constants.PREF_LAST_RUN_TIME, -triggeredSyncSleepIntervalS * 1000);
            editor.apply();
            lastSyncTimeSinceBootMillisecs = 0;
        }

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();

        // Initially schedule the SyncTrigger job.
        int elapsedSecondsSinceLastSync = (int) (elapsedRealtime - lastSyncTimeSinceBootMillisecs) / 1000;
        Log.d(TAG, "JobPrepare: mTimeConditionMatch=" + mTimeConditionMatch.toString() +
                ", elapsedRealtime=" + elapsedRealtime +
                ", lastSyncTimeSinceBootMillisecs=" + lastSyncTimeSinceBootMillisecs +
                ", elapsedSecondsSinceLastSync=" + elapsedSecondsSinceLastSync
        );
        JobUtils.scheduleSyncTriggerServiceJob(
                context,
                mTimeConditionMatch ?
                    triggeredSyncDurationS :
                       /**
                        * if triggeredSyncSleepIntervalS - elapsedSecondsSinceLastSync is < 0,
                        * mTimeConditionMatch is set to true during updateShouldRunDecision().
                        * Thus the false case cannot be triggered if the delay for scheduleSyncTriggerServiceJob would be negative
                        */
                        triggeredSyncSleepIntervalS - elapsedSecondsSinceLastSync,
                !mTimeConditionMatch
        );
    }

    public void shutdown() {
        LogV("Shutting down");
        JobUtils.cancelAllScheduledJobs(mContext);
        if (mSyncStatusObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncStatusObserverHandle);
            mSyncStatusObserverHandle = null;
        }

        // SyncTriggerReceiver
        if (mSyncTriggerReceiver != null) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            localBroadcastManager.unregisterReceiver(mSyncTriggerReceiver);
            mSyncTriggerReceiver = null;
        }

        // UpdateShouldRunDecisionReceiver
        if (mUpdateShouldRunDecisionReceiver != null) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            localBroadcastManager.unregisterReceiver(mUpdateShouldRunDecisionReceiver);
            mUpdateShouldRunDecisionReceiver = null;
        }
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    || Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                SystemClock.sleep(5000);
                updateShouldRunDecision();
            }
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class PowerSaveModeChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class SyncTriggerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mRunAllowedStopScheduled = false;
            boolean extraBeginActiveTimeWindow = intent.getBooleanExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, false);
            LogV("SyncTriggerReceiver: onReceive, extraBeginActiveTimeWindow=" + Boolean.toString(extraBeginActiveTimeWindow));

            boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);
            if (!prefRunOnTimeSchedule) {
                /**
                 * The feature is currently disabled.
                 * Reschedule the job to see if the user turned on this feature in the meantime.
                 */
                mTimeConditionMatch = false;
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncSleepIntervalS,
                        true
                );
                return;
            }

            // extraBeginActiveTimeWindow determines whether syncthing should start or stop
            if (extraBeginActiveTimeWindow) {
                // We should immediately start SyncthingNative for TRIGGERED_SYNC_DURATION_SECS.
                mTimeConditionMatch = true;
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncDurationS,
                        false
                );
                mRunAllowedStopScheduled = true;
            } else {
                /**
                 * Toggle the "digital input" for this condition as the condition change is
                 * triggered by a time schedule.
                 */
                mTimeConditionMatch = false;
                /**
                 * If Syncthing is running and the last run was more than triggeredSyncSleepIntervalS ago,
                 * this stop job might actually start Syncthing (resp. leave it running) because
                 * mTimeConditionsMatch is switched to true if last run was more than triggeredSyncSleepIntervalS ago.
                 * So in this case we put a new (fake) last run time slightly less than triggeredSyncSleepIntervalS ago.
                 * If Syncthing really is stopped (which it should) then the wrong time gets
                 * corrected immediately
                 */
                long lastRunTimeMillis = mPreferences.getLong(Constants.PREF_LAST_RUN_TIME, 0);
                if (lastDeterminedShouldRun && SystemClock.elapsedRealtime() - lastRunTimeMillis > triggeredSyncSleepIntervalS * 1000) {
                    SharedPreferences.Editor editor = mPreferences.edit();
                    editor.putLong(Constants.PREF_LAST_RUN_TIME, SystemClock.elapsedRealtime() - triggeredSyncSleepIntervalS * 1000 + 60*1000);
                    editor.apply();
                }
            }
            updateShouldRunDecision();

            /**
             * Reschedule the job.
             * If we are within a "SyncthingNative shouldn't run" time frame,
             * let the receiver fire and change to "SyncthingNative should run" after
             * triggeredSyncSleepIntervalS seconds elapsed.
             * If we are within a "SyncthingNative should run" time frame,
             * the change to "SyncthingNative shouldn't run" after
             * TRIGGERED_SYNC_DURATION_SECS seconds elapsed should actually
             * be scheduled inside updateShouldRunDecision(), but his might
             * not always be the case.
             * Thus we schedule an additional change to "SyncthingNative shouldn't run"
             * after TRIGGERED_SYNC_DURATION_SECS seconds elapsed, but without
             * cancelling other jobs. This should only serve as a backup job and
             * will not fire if the job inside updateShouldRunDecision() is
             * scheduled correctly.
             */
            if (!mRunAllowedStopScheduled && !lastDeterminedShouldRun) {
                JobUtils.cancelAllScheduledJobs(context);
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncSleepIntervalS,
                        true
                );
            } else {
                JobUtils.scheduleSyncTriggerServiceJob(
                        context,
                        triggeredSyncDurationS,
                        false);
            }
        }
    }

    private class UpdateShouldRunDecisionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogV("UpdateShouldRunDecisionReceiver: onReceive");
            updateShouldRunDecision();
        }
    }

    /**
     * Event handler that is fired after preconditions changed.
     * We then need to decide if syncthing should run.
     */
    public void updateShouldRunDecision() {
        if (!Constants.isRunningOnEmulator()) {
            triggeredSyncDurationS = RunConditionEvaluator.syncDurationMinutes(mPreferences) * 60;
            triggeredSyncSleepIntervalS = RunConditionEvaluator.sleepIntervalMinutes(mPreferences) * 60;
        }

        boolean newShouldRun = decideShouldRun();
        if (newShouldRun) {
            /**
             * Trigger:
             *  a) Sync pre-conditions changed
             *      a1) AND SyncthingService.State should remain ACTIVE
             *      a2) AND SyncthingService.State should transition from INIT/DISABLED to ACTIVE
             *  b) Sync pre-conditions did not change
             *      b1) AND SyncthingService.State should remain ACTIVE
             *          because a reevaluation of the run conditions was forced from code.
             * Action:
             *  SyncthingService will evaluate custom per-object run conditions
             *  and pause/unpause objects accordingly.
             */
             if (mOnSyncPreconditionChangedListener != null) {
                 mOnSyncPreconditionChangedListener.onSyncPreconditionChanged(this);
             }
        }

        /**
         * Check if the current conditions changed the result of decideShouldRun()
         * compared to the last determined result.
         */
        if (newShouldRun != lastDeterminedShouldRun) {
            /**
             * Notify SyncthingService in case it has to transition from
             * a) INIT/DISABLED => STARTING => ACTIVE
             * b) ACTIVE => DISABLED
             */
            if (mOnShouldRunChangedListener != null) {
                mOnShouldRunChangedListener.onShouldRunDecisionChanged(newShouldRun);
                lastDeterminedShouldRun = newShouldRun;
            }
            if (newShouldRun &&
                    !mRunAllowedStopScheduled &&
                    mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false) &&
                    mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP) == Constants.BTNSTATE_NO_FORCE_START_STOP) {
                JobUtils.cancelAllScheduledJobs(mContext);
                JobUtils.scheduleSyncTriggerServiceJob(
                        mContext,
                        triggeredSyncDurationS,
                        false
                );
                mRunAllowedStopScheduled = true;
            }
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putLong(Constants.PREF_LAST_RUN_TIME, SystemClock.elapsedRealtime());
            editor.apply();
        }
    }

    public String getRunDecisionExplanation() {
        return mRunDecisionExplanation;
    }

    /**
     * Determines if Syncthing should currently run.
     * Updates mRunDecisionExplanation and mTimeConditionMatch.
     */
    private boolean decideShouldRun() {
        DeviceConditions device = readDeviceConditions();
        RunConditionEvaluator.Decision decision = RunConditionEvaluator.decide(
                mPreferences, device, SystemClock.elapsedRealtime(), mTimeConditionMatch);
        mTimeConditionMatch = decision.timeConditionMatch;
        mRunDecisionExplanation = formatExplanation(res, decision);
        LogV("decideShouldRun: " + device + ", reasons=" + decision.reasons + " => " + decision.shouldRun);
        return decision.shouldRun;
    }

    /**
     * Check if an object's individual sync conditions are met.
     * Precondition: Object must own pref "...CustomSyncConditionsEnabled == true".
     */
    public Boolean checkObjectSyncConditions(String objectPrefixAndId) {
        boolean met = RunConditionEvaluator.checkObjectConditions(
                mPreferences, readDeviceConditions(), objectPrefixAndId);
        LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): " + met);
        return met;
    }

    /**
     * Builds the run-decision explanation shown in the app from the decision's reasons.
     */
    static String formatExplanation(Resources res, RunConditionEvaluator.Decision decision) {
        StringBuilder explanation = new StringBuilder();
        for (RunConditionEvaluator.Reason reason : decision.reasons) {
            if (reason == RunConditionEvaluator.Reason.NOT_WITHIN_TIME_FRAME) {
                int minutes = decision.minutesSinceLastRun;
                String minutesText = (minutes == 0)
                        ? res.getString(R.string.reason_not_within_time_frame_0_min)
                        : String.format(res.getQuantityString(R.plurals.reason_not_within_time_frame_minutes, minutes), minutes);
                explanation.append(String.format(res.getString(reason.stringRes), minutesText));
                continue;
            }
            if (reason.newlineBefore) {
                explanation.append("\n");
            }
            explanation.append(res.getString(reason.stringRes));
        }
        return explanation.toString();
    }

    /**
     * Reads the current device state. The only place that queries the Android framework
     * for run-condition inputs.
     */
    private DeviceConditions readDeviceConditions() {
        DeviceConditions.Builder builder = new DeviceConditions.Builder()
                .charging(isCharging())
                .powerSaving(isPowerSaving())
                .masterSyncEnabled(ContentResolver.getMasterSyncAutomatically());

        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            builder.transport(DeviceConditions.Transport.NO_ACTIVE_NETWORK);
        } else if (!ni.isConnected()) {
            builder.transport(DeviceConditions.Transport.DISCONNECTED);
        } else {
            switch (ni.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                case ConnectivityManager.TYPE_WIMAX:
                    builder.transport(DeviceConditions.Transport.WIFI);
                    break;
                case ConnectivityManager.TYPE_ETHERNET:
                    builder.transport(DeviceConditions.Transport.ETHERNET);
                    break;
                case ConnectivityManager.TYPE_BLUETOOTH:
                case ConnectivityManager.TYPE_MOBILE:
                case ConnectivityManager.TYPE_MOBILE_DUN:
                case ConnectivityManager.TYPE_MOBILE_HIPRI:
                    builder.transport(DeviceConditions.Transport.MOBILE);
                    break;
                default:
                    builder.transport(DeviceConditions.Transport.OTHER);
                    break;
            }
            builder.activeNetworkMetered(cm.isActiveNetworkMetered());
            builder.roaming(ni.isRoaming());
        }

        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = (wifiManager != null) ? wifiManager.getConnectionInfo() : null;
        if (wifiInfo != null) {
            // DO NOT RELEASE WITH A LOG LINE PRINTING THE SSID.
            builder.wifiInfoAvailable(true).wifiSsid(wifiInfo.getSSID());
        }
        return builder.build();
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isCharging() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            LogV("isCharging: Checking battery status intent returned null");
            return false;
        }
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        LogV("isCharging: Battery status intent extras: " + (intent.getExtras() != null ? intent.getExtras().toString() : "null"));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
            plugged == BatteryManager.BATTERY_PLUGGED_DOCK;
    }

    private boolean isPowerSaving() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(TAG, "getSystemService(POWER_SERVICE) unexpectedly returned NULL.");
            return false;
        }
        return powerManager.isPowerSaveMode();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
