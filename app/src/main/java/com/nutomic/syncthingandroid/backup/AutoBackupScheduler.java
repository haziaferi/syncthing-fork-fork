package com.nutomic.syncthingandroid.backup;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Schedules the automatic backup after changes (spec R13): 10 minutes after the last change,
 * but no later than 60 minutes after the first change not yet covered.
 */
public final class AutoBackupScheduler {

    private static final String TAG = "AutoBackupScheduler";

    public static final int JOB_ID = 1001;
    public static final long DEBOUNCE_MS = 10 * 60 * 1000L;
    public static final long MAX_WAIT_MS = 60 * 60 * 1000L;
    /** Private preferences file; never backed up and never watched. */
    public static final String STATE_PREFS = "auto_backup_state";
    /** Epoch ms of the first change the next backup covers; 0 when none is pending. */
    public static final String KEY_FIRST_CHANGE = "first_change_at";

    /** Kept here because SharedPreferences holds its listeners weakly. */
    @Nullable
    private static SharedPreferences.OnSharedPreferenceChangeListener sListener;

    private AutoBackupScheduler() {
    }

    public static long delayMillis(long now, long firstChangeAt) {
        long runAt = Math.min(now + DEBOUNCE_MS, firstChangeAt + MAX_WAIT_MS);
        return Math.max(0, runAt - now);
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    /** Records a change and schedules the backup job. Does nothing while the feature is off. */
    public static void noteChange(Context context, Clock clock) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!AutoBackupPrefs.isEnabled(prefs)) {
            return;
        }
        long now = clock.nowMillis();
        SharedPreferences state = state(context);
        long firstChangeAt = state.getLong(KEY_FIRST_CHANGE, 0L);
        if (firstChangeAt == 0L) {
            firstChangeAt = now;
            state.edit().putLong(KEY_FIRST_CHANGE, firstChangeAt).commit();
        }
        scheduleIn(context, delayMillis(now, firstChangeAt));
    }

    /** Schedules the backup job to run after {@code delayMs}, replacing any pending one. */
    public static void scheduleIn(Context context, long delayMs) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, AutoBackupJobService.class))
                .setMinimumLatency(delayMs)
                .setPersisted(true)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
        Log.v(TAG, "Scheduled the automatic backup in " + delayMs / 1000 + " s");
    }

    /** Cancels the pending backup job and forgets the pending change. */
    public static void cancel(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(JOB_ID);
        clearPendingChange(context);
    }

    public static void clearPendingChange(Context context) {
        state(context).edit().remove(KEY_FIRST_CHANGE).commit();
    }

    /** Applies the trigger rules to a change of {@code key}; a null key means the prefs were cleared. */
    public static void onPreferenceChanged(Context context, SharedPreferences prefs, @Nullable String key,
                                           Clock clock) {
        if (Constants.PREF_BACKUP_PASSWORD.equals(key)
                && prefs.getString(Constants.PREF_BACKUP_PASSWORD, "").isEmpty()
                && AutoBackupPrefs.isEnabled(prefs)) {
            // A backup cannot be written without its password (R19).
            prefs.edit().putBoolean(AutoBackupPrefs.KEY_ENABLED, false).commit();
            AutoBackupPrefs.recordFailure(prefs, clock.nowMillis(), AutoBackupPrefs.FailureReason.PASSWORD_REMOVED);
            cancel(context);
            return;
        }
        if (AutoBackupPrefs.KEY_ENABLED.equals(key)) {
            if (AutoBackupPrefs.isEnabled(prefs)) {
                noteChange(context, clock);
            } else {
                cancel(context);
            }
            return;
        }
        if (AutoBackupPrefs.isRuntimeKey(key)) {
            return;
        }
        noteChange(context, clock);
    }

    /** Watches the app's default preferences for the rest of the process. */
    public static void register(Context appContext) {
        final Context context = appContext.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (sListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(sListener);
        }
        sListener = (changed, key) -> onPreferenceChanged(context, changed, key, Clock.SYSTEM);
        prefs.registerOnSharedPreferenceChangeListener(sListener);
    }
}
