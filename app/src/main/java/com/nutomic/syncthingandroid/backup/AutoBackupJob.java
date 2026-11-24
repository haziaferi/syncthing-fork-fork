package com.nutomic.syncthingandroid.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.util.PermissionUtil;

/** One run of the automatic backup job. Runs off the main thread. */
public final class AutoBackupJob {

    public enum Result { DISABLED, DEFERRED, WRITTEN, SKIPPED_UNCHANGED, FAILED }

    private AutoBackupJob() {
    }

    public static Result execute(Context context, Clock clock, AutoBackupWriter.ZipWriter zipWriter) {
        return execute(context, clock, zipWriter, PermissionUtil.haveStoragePermission(context));
    }

    /** As {@link #execute(Context, Clock, AutoBackupWriter.ZipWriter)}, with the permission given. */
    static Result execute(Context context, Clock clock, AutoBackupWriter.ZipWriter zipWriter,
                          boolean haveStorageAccess) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!AutoBackupPrefs.isEnabled(prefs)) {
            AutoBackupScheduler.cancel(context);
            return Result.DISABLED;
        }
        if (AutoBackupGate.isBlocked(context.getFilesDir())) {
            // A manual export or import is running, or a rollback is unfinished (R14).
            AutoBackupScheduler.scheduleIn(context, AutoBackupScheduler.DEBOUNCE_MS);
            return Result.DEFERRED;
        }
        // Cleared before the run, so a change made during it schedules its own backup.
        AutoBackupScheduler.clearPendingChange(context);
        AutoBackupWriter.Outcome outcome = new AutoBackupWriter(
                context.getFilesDir(),
                Environment.getExternalStorageDirectory(),
                prefs,
                clock,
                haveStorageAccess,
                zipWriter).run();
        switch (outcome) {
            case WRITTEN:
                return Result.WRITTEN;
            case SKIPPED_UNCHANGED:
                return Result.SKIPPED_UNCHANGED;
            default:
                return Result.FAILED;
        }
    }
}
