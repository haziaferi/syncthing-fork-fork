package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.res.Resources;
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

    /** Re-evaluate at least this long after the last evaluation, in case a callback was missed (spec R04). */
    static final long RECHECK_MS = 15 * 60 * 1000L;

    /** Key of the whole-app decision in {@link #mStopDelay}. */
    private static final String APP_KEY = "";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final StopDelay mStopDelay = new StopDelay();
    private final Runnable mReevaluate = this::updateShouldRunDecision;
    private final Runnable mHoldEnd = this::updateShouldRunDecision;
    private NetworkTracker mNetworkTracker;
    private boolean mShutdown = false;
    /**
     * Whether the last decision was a stop that never waits: the user's force-stop, or the end
     * of a time-schedule window, whose job rescheduling relies on an immediate stop (spec R09).
     */
    private boolean mLastDecisionStopsAtOnce = false;

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
        this(context, null, onShouldRunChangedListener, onSyncPreconditionChangedListener);
    }

    /**
     * @param preferences the app's preferences, or null to have them injected. Tests pass them,
     *                    so they need no {@link SyncthingApp}.
     */
    RunConditionMonitor(Context context, @Nullable SharedPreferences preferences,
            OnShouldRunChangedListener onShouldRunChangedListener,
            OnSyncPreconditionChangedListener onSyncPreconditionChangedListener) {
        if (preferences != null) {
            mPreferences = preferences;
        } else {
            ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        }
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("Created new instance");
        mContext = context;
        res = mContext.getResources();
        mOnShouldRunChangedListener = onShouldRunChangedListener;
        mOnSyncPreconditionChangedListener = onSyncPreconditionChangedListener;

        // Network changes arrive through callbacks (spec R03). Started before the first
        // decision, so that decision already sees the connected networks.
        mNetworkTracker = new NetworkTracker(mContext, this::updateShouldRunDecision);
        mNetworkTracker.start();

        /**
         * Register broadcast receivers.
         */
        // Screen on: re-check in case a network callback was missed (spec R04).
        ReceiverManager.registerReceiver(mContext, new ScreenOnReceiver(), new IntentFilter(Intent.ACTION_SCREEN_ON));

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
        try {
            mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);
        } catch (RuntimeException e) {
            // Without it, the 15-minute re-check still picks up the toggle (spec R04).
            Log.w(TAG, "addStatusChangeListener failed", e);
        }

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
        mShutdown = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mNetworkTracker != null) {
            mNetworkTracker.stop();
        }
        JobUtils.cancelSyncTriggerJob(mContext);
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

    private class ScreenOnReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
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
                JobUtils.cancelSyncTriggerJob(context);
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
                JobUtils.cancelSyncTriggerJob(context);
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
                JobUtils.cancelSyncTriggerJob(context);
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
        if (mShutdown) {
            return;
        }
        if (!Constants.isRunningOnEmulator()) {
            triggeredSyncDurationS = RunConditionEvaluator.syncDurationMinutes(mPreferences) * 60;
            triggeredSyncSleepIntervalS = RunConditionEvaluator.sleepIntervalMinutes(mPreferences) * 60;
        }

        boolean rawShouldRun = decideShouldRun();
        boolean newShouldRun;
        if (mLastDecisionStopsAtOnce) {
            // Force-stop and a time-window end never wait, and nothing they stopped is held (spec R09).
            mStopDelay.clear();
            newShouldRun = false;
        } else {
            newShouldRun = mStopDelay.effective(APP_KEY, rawShouldRun, SystemClock.elapsedRealtime());
            if (!newShouldRun) {
                // Folders and devices are not checked while Syncthing is stopped (spec R09).
                mStopDelay.clearExcept(APP_KEY);
            }
        }
        scheduleReevaluations();
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
                JobUtils.cancelSyncTriggerJob(mContext);
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

    /**
     * Re-evaluates {@link #RECHECK_MS} after this evaluation (spec R04), and when the stop
     * delay's earliest hold ends (spec R09).
     */
    private void scheduleReevaluations() {
        mHandler.removeCallbacks(mReevaluate);
        mHandler.postDelayed(mReevaluate, RECHECK_MS);
        mHandler.removeCallbacks(mHoldEnd);
        long now = SystemClock.elapsedRealtime();
        long holdEnd = mStopDelay.nextCheckAt(now);
        if (holdEnd != -1L) {
            mHandler.postDelayed(mHoldEnd, holdEnd - now);
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
        mLastDecisionStopsAtOnce = decision.reasons.contains(RunConditionEvaluator.Reason.FORCE_STOP)
                || decision.reasons.contains(RunConditionEvaluator.Reason.NOT_WITHIN_TIME_FRAME);
        mRunDecisionExplanation = formatExplanation(res, decision);
        LogV("decideShouldRun: " + device + ", reasons=" + decision.reasons + " => " + decision.shouldRun);
        return decision.shouldRun;
    }

    /**
     * Check if an object's individual sync conditions are met.
     * Precondition: Object must own pref "...CustomSyncConditionsEnabled == true".
     */
    public Boolean checkObjectSyncConditions(String objectPrefixAndId) {
        boolean raw = RunConditionEvaluator.checkObjectConditions(
                mPreferences, readDeviceConditions(), objectPrefixAndId);
        // A folder or device pauses only after its conditions stay unmet for 10 seconds (spec R09).
        boolean met = mStopDelay.effective(objectPrefixAndId, raw, SystemClock.elapsedRealtime());
        if (met != raw) {
            mHandler.post(this::scheduleReevaluations);
        }
        LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): " + raw + (met != raw ? ", held" : ""));
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
     * Reads the current device state. Network fields come from {@link NetworkTracker}
     * through {@link NetworkSnapshot} (spec R05, R07, R08).
     */
    private DeviceConditions readDeviceConditions() {
        DeviceConditions.Builder builder = new DeviceConditions.Builder()
                .charging(isCharging())
                .powerSaving(isPowerSaving())
                .masterSyncEnabled(ContentResolver.getMasterSyncAutomatically());
        // DO NOT RELEASE WITH A LOG LINE PRINTING THE SSID.
        NetworkSnapshot.fill(builder, mNetworkTracker.snapshot());
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
