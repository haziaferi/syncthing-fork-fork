package com.nutomic.syncthingandroid.backup;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Settings and status of the automatic config backup (spec R15, R16, R22).
 * Every write uses commit(), so a job that ends right after still saves it.
 */
public final class AutoBackupPrefs {

    public static final String KEY_ENABLED = "auto_backup_enabled";
    public static final String KEY_REL_FOLDER = "auto_backup_rel_folder";
    /** Long, epoch ms of the last good backup. */
    public static final String KEY_LAST_SUCCESS = "auto_backup_last_success";
    /** String {@code <epoch ms>;<reason name>}. */
    public static final String KEY_LAST_FAILURE = "auto_backup_last_failure";
    public static final String KEY_LAST_HASH = "auto_backup_last_hash";
    public static final String DEFAULT_REL_FOLDER = "backups/syncthing/auto";

    /** Device-specific status: left out of backups, never overwritten by an import. */
    public static final Set<String> STATUS_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(KEY_LAST_SUCCESS, KEY_LAST_FAILURE, KEY_LAST_HASH)));

    /** Keys the app writes while it runs (R22), besides the status keys. */
    private static final Set<String> RUNTIME_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
                    Constants.PREF_LAST_RUN_TIME,
                    Constants.PREF_APP_START_COUNTER,
                    Constants.PREF_DEBUG_FACILITIES_AVAILABLE,
                    Constants.PREF_LAST_BINARY_VERSION,
                    Constants.PREF_BTNSTATE_FORCE_START_STOP,
                    ShareActivity.PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER)));

    public enum FailureReason {
        FOLDER_NOT_WRITABLE,
        NO_STORAGE_ACCESS,
        OUT_OF_SPACE,
        CHECK_FAILED,
        PASSWORD_REMOVED,
        RETENTION_FAILED
    }

    public static final class Failure {
        public final long timeMillis;
        public final FailureReason reason;

        Failure(long timeMillis, FailureReason reason) {
            this.timeMillis = timeMillis;
            this.reason = reason;
        }
    }

    private AutoBackupPrefs() {
    }

    public static boolean isRuntimeKey(@Nullable String key) {
        if (key == null) {
            return false;
        }
        return RUNTIME_KEYS.contains(key)
                || STATUS_KEYS.contains(key)
                || key.startsWith(Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX)
                || key.startsWith(ShareActivity.PREF_FOLDER_SAVED_SUBDIRECTORY);
    }

    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    /**
     * Turns the feature on or off. Turning it on is refused while no backup
     * password is set (R05).
     *
     * @return false if refused.
     */
    public static boolean setEnabled(SharedPreferences prefs, boolean on) {
        if (on && prefs.getString(Constants.PREF_BACKUP_PASSWORD, "").isEmpty()) {
            return false;
        }
        prefs.edit().putBoolean(KEY_ENABLED, on).commit();
        return true;
    }

    public static File backupFolder(SharedPreferences prefs, File sharedStorageRoot) {
        String rel = prefs.getString(KEY_REL_FOLDER, DEFAULT_REL_FOLDER);
        if (rel == null || rel.isEmpty()) {
            rel = DEFAULT_REL_FOLDER;
        }
        return new File(sharedStorageRoot, rel);
    }

    public static void recordSuccess(SharedPreferences prefs, long timeMillis, String hash) {
        prefs.edit()
                .putLong(KEY_LAST_SUCCESS, timeMillis)
                .putString(KEY_LAST_HASH, hash)
                .commit();
    }

    public static void recordFailure(SharedPreferences prefs, long timeMillis, FailureReason reason) {
        prefs.edit().putString(KEY_LAST_FAILURE, timeMillis + ";" + reason.name()).commit();
    }

    @Nullable
    public static Failure lastFailure(SharedPreferences prefs) {
        String value = prefs.getString(KEY_LAST_FAILURE, null);
        if (value == null) {
            return null;
        }
        int sep = value.indexOf(';');
        if (sep < 0) {
            return null;
        }
        try {
            long time = Long.parseLong(value.substring(0, sep));
            FailureReason reason = FailureReason.valueOf(value.substring(sep + 1));
            return new Failure(time, reason);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
