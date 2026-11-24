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

    /**
     * Records a change and schedules the backup job. Does nothing while the feature is off.
     * While a run is in progress the change is only recorded: scheduling the job's own ID
     * would stop the running job, so the run schedules it when it ends.
     */
    public static void noteChange(Context context, Clock clock) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!AutoBackupPrefs.isEnabled(prefs)) {
            return;
        }
        long now = clock.nowMillis();
        long firstChangeAt = recordChange(context, now);
        if (AutoBackupJob.isRunning()) {
            return;
        }
        scheduleIn(context, delayMillis(now, firstChangeAt));
    }

    /**
     * Sets the pending change time to {@code at} unless an earlier one is pending; returns it.
     * apply() updates memory at once and writes to disk in the background, since this often
     * runs on the main thread (a config save or a settings change).
     */
    private static synchronized long recordChange(Context context, long at) {
        SharedPreferences state = state(context);
        long firstChangeAt = state.getLong(KEY_FIRST_CHANGE, 0L);
        if (firstChangeAt == 0L || at < firstChangeAt) {
            firstChangeAt = at;
            state.edit().putLong(KEY_FIRST_CHANGE, firstChangeAt).apply();
        }
        return firstChangeAt;
    }

    /** The time of the first change no backup covers yet, or 0. */
    public static synchronized long pendingChangeAt(Context context) {
        return state(context).getLong(KEY_FIRST_CHANGE, 0L);
    }

    /** Returns the pending change time (0 if none) and clears it, as a run starts. */
    public static synchronized long takePendingChange(Context context) {
        long firstChangeAt = pendingChangeAt(context);
        clearPendingChange(context);
        return firstChangeAt;
    }

    /**
     * Puts back a change a run did not cover. {@code since} 0 means the time is unknown;
     * the call then records nothing new if a change is already pending.
     */
    public static void restorePendingChange(Context context, long since, Clock clock) {
        if (since == 0L) {
            if (pendingChangeAt(context) != 0L) {
                return;
            }
            since = clock.nowMillis();
        }
        recordChange(context, since);
    }

    /** Schedules the job for the pending change, if there is one and the feature is on. */
    public static void scheduleIfPending(Context context, Clock clock) {
        if (!AutoBackupPrefs.isEnabled(PreferenceManager.getDefaultSharedPreferences(context))) {
            return;
        }
        long firstChangeAt = pendingChangeAt(context);
        if (firstChangeAt != 0L) {
            scheduleIn(context, delayMillis(clock.nowMillis(), firstChangeAt));
        }
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

    public static synchronized void clearPendingChange(Context context) {
        state(context).edit().remove(KEY_FIRST_CHANGE).apply();
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
