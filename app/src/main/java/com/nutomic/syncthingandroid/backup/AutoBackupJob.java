package com.nutomic.syncthingandroid.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.util.PermissionUtil;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** One run of the automatic backup job. Runs off the main thread. */
public final class AutoBackupJob {

    public enum Result { DISABLED, DEFERRED, WRITTEN, SKIPPED_UNCHANGED, FAILED }

    private AutoBackupJob() {
    }

    public static Result execute(Context context, Clock clock, AutoBackupWriter.ZipWriter zipWriter) {
        return execute(context, clock, zipWriter, PermissionUtil.haveStoragePermission(context));
    }

    /** True while a run is in progress in this process. */
    private static final AtomicBoolean sRunning = new AtomicBoolean();

    static boolean isRunning() {
        return sRunning.get();
    }

    /** As {@link #execute(Context, Clock, AutoBackupWriter.ZipWriter)}, with the permission given. */
    static Result execute(Context context, Clock clock, AutoBackupWriter.ZipWriter zipWriter,
                          boolean haveStorageAccess) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!AutoBackupPrefs.isEnabled(prefs)) {
            AutoBackupScheduler.cancel(context);
            return Result.DISABLED;
        }
        if (!sRunning.compareAndSet(false, true)) {
            // Another run is in progress; it schedules the next one when it ends. Scheduling
            // here would stop it.
            AutoBackupScheduler.restorePendingChange(context, clock.nowMillis());
            return Result.DEFERRED;
        }
        long pendingSince;
        AutoBackupWriter.Outcome outcome;
        try {
            File filesDir = context.getFilesDir();
            if (AutoBackupGate.isBlocked(filesDir)) {
                // A manual export or import is running, or a rollback is unfinished (R14).
                AutoBackupScheduler.scheduleIn(context, AutoBackupScheduler.DEBOUNCE_MS);
                return Result.DEFERRED;
            }
            // Taken before the run, so a change made during it is recorded afresh.
            pendingSince = AutoBackupScheduler.takePendingChange(context);
            final int generation = AutoBackupGate.generation();
            outcome = new AutoBackupWriter(
                    filesDir,
                    Environment.getExternalStorageDirectory(),
                    prefs,
                    clock,
                    haveStorageAccess,
                    zipWriter,
                    () -> AutoBackupGate.generation() != generation || AutoBackupGate.isBlocked(filesDir)).run();
        } finally {
            sRunning.set(false);
        }

        boolean changedDuringRun = AutoBackupScheduler.pendingChangeAt(context) != 0L;
        Result result;
        switch (outcome) {
            case WRITTEN:
                result = Result.WRITTEN;
                break;
            case SKIPPED_UNCHANGED:
                result = Result.SKIPPED_UNCHANGED;
                break;
            case INTERRUPTED:
                // Try again once the manual operation is likely over (R14).
                AutoBackupScheduler.restorePendingChange(context, pendingSince);
                AutoBackupScheduler.scheduleIn(context, AutoBackupScheduler.DEBOUNCE_MS);
                return Result.DEFERRED;
            default:
                // The change is still not in a backup. It is not retried on its own, so a
                // lasting failure does not loop; the next change runs it at once.
                AutoBackupPrefs.Failure failure = AutoBackupPrefs.lastFailure(prefs);
                if (failure == null || failure.reason != AutoBackupPrefs.FailureReason.PASSWORD_REMOVED) {
                    AutoBackupScheduler.restorePendingChange(context, pendingSince);
                }
                result = Result.FAILED;
                break;
        }
        if (changedDuringRun) {
            AutoBackupScheduler.scheduleIfPending(context, clock);
        }
        return result;
    }
}
