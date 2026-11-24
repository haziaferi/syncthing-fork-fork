package com.nutomic.syncthingandroid.backup;

import android.content.SharedPreferences;
import android.util.Log;

import com.nutomic.syncthingandroid.backup.AutoBackupPrefs.FailureReason;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.ConfigArchive;
import com.nutomic.syncthingandroid.util.ConfigImporter;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.UnzipParameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and checks one automatic backup (spec R03, R04, R05, R10, R11, R13, R19).
 * It never stops Syncthing and never touches the manual export's files (R07).
 */
public final class AutoBackupWriter {

    private static final String TAG = "AutoBackupWriter";

    /** Writes an archive of {@code paths} to {@code target}. */
    public interface ZipWriter {
        void write(File target, List<File> paths, String password) throws IOException;
    }

    /** True once something happened that makes the archive being written unsafe to keep. */
    public interface Interference {
        boolean happened();
    }

    /**
     * INTERRUPTED: a manual export or import started during the run (R14); nothing was kept
     * and nothing was recorded.
     */
    public enum Outcome { WRITTEN, SKIPPED_UNCHANGED, FAILED, INTERRUPTED }

    /** Work folder inside the app's files folder; separate from the manual export's files. */
    public static final String WORK_DIR = "auto-backup-work";
    public static final long MIN_FREE_BYTES = 1024 * 1024;
    public static final String[] REQUIRED_ENTRIES = {
            Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE};

    private final File filesDir;
    private final File sharedStorageRoot;
    private final SharedPreferences prefs;
    private final Clock clock;
    private final boolean haveStorageAccess;
    private final ZipWriter zipWriter;
    private final Interference interference;

    /** A step that failed, with the reason to record. */
    private static final class StepFailed extends Exception {
        final FailureReason reason;

        StepFailed(FailureReason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    public AutoBackupWriter(File filesDir, File sharedStorageRoot, SharedPreferences prefs, Clock clock,
                            boolean haveStorageAccess, ZipWriter zipWriter) {
        this(filesDir, sharedStorageRoot, prefs, clock, haveStorageAccess, zipWriter, () -> false);
    }

    public AutoBackupWriter(File filesDir, File sharedStorageRoot, SharedPreferences prefs, Clock clock,
                            boolean haveStorageAccess, ZipWriter zipWriter, Interference interference) {
        this.interference = interference;
        this.filesDir = filesDir;
        this.sharedStorageRoot = sharedStorageRoot;
        this.prefs = prefs;
        this.clock = clock;
        this.haveStorageAccess = haveStorageAccess;
        this.zipWriter = zipWriter;
    }

    static boolean isOutOfSpace(IOException e) {
        String message = e.getMessage();
        return message != null && (message.contains("ENOSPC") || message.contains("No space left"));
    }

    /**
     * Runs the steps in order and stops at the first failure, which is recorded. Existing
     * backups are never touched by a failed run; the work folder is deleted on every path.
     */
    public Outcome run() {
        long now = clock.nowMillis();
        File workDir = new File(filesDir, WORK_DIR);
        File tmp = null;
        try {
            // 1. A backup needs its password (R05, R19).
            String password = prefs.getString(Constants.PREF_BACKUP_PASSWORD, "");
            if (password == null || password.isEmpty()) {
                AutoBackupPrefs.setEnabled(prefs, false);
                throw new StepFailed(FailureReason.PASSWORD_REMOVED, "backup password is empty");
            }
            // 2. Storage permission.
            if (!haveStorageAccess) {
                throw new StepFailed(FailureReason.NO_STORAGE_ACCESS, "no storage permission");
            }
            // 3. The backup folder (R09).
            File folder = AutoBackupPrefs.backupFolder(prefs, sharedStorageRoot);
            folder.mkdirs();
            if (!folder.isDirectory() || !folder.canWrite()) {
                throw new StepFailed(FailureReason.FOLDER_NOT_WRITABLE, "cannot write to " + folder);
            }
            // 4. The included files (R03), and the settings copy in the work folder.
            List<File> files = includedFiles();
            ConfigImporter.deleteQuietly(workDir);
            File prefsCopy = new File(workDir, Constants.SHARED_PREFS_FILE);
            Map<String, ?> allPrefs = prefs.getAll();
            try {
                if (!workDir.mkdirs()) {
                    throw new IOException("cannot create " + workDir);
                }
                Map<String, Object> backedUp = new HashMap<>(allPrefs);
                backedUp.keySet().removeAll(AutoBackupPrefs.STATUS_KEYS);
                ConfigArchive.writePrefs(backedUp, prefsCopy);
            } catch (IOException e) {
                throw new StepFailed(FailureReason.FOLDER_NOT_WRITABLE,
                        "cannot write the settings copy: " + e.getMessage());
            }
            // 5. Skip when nothing changed and a backup still exists (R13).
            String hash;
            try {
                hash = ContentHash.of(files, allPrefs);
            } catch (IOException e) {
                throw new StepFailed(FailureReason.CHECK_FAILED, "cannot read the included files: " + e.getMessage());
            }
            if (hash.equals(prefs.getString(AutoBackupPrefs.KEY_LAST_HASH, null))
                    && !BackupFolder.list(folder).isEmpty()) {
                Log.d(TAG, "Content unchanged, skipping the backup");
                return Outcome.SKIPPED_UNCHANGED;
            }
            // 6. Free space.
            if (folder.getUsableSpace() < MIN_FREE_BYTES) {
                throw new StepFailed(FailureReason.OUT_OF_SPACE, "less than 1 MiB free in " + folder);
            }
            // 7. Write the archive to a .tmp file (R10 step 1).
            // Named no earlier than the newest backup, so it orders newest even if the wall
            // clock was set back (R12).
            File target = BackupFolder.nextFile(folder, BackupFolder.nameTime(folder, now));
            tmp = new File(folder, target.getName() + BackupFolder.TMP_SUFFIX);
            List<File> archived = new ArrayList<>(files);
            archived.add(prefsCopy);
            try {
                zipWriter.write(tmp, archived, password);
            } catch (IOException e) {
                throw new StepFailed(isOutOfSpace(e) ? FailureReason.OUT_OF_SPACE : FailureReason.FOLDER_NOT_WRITABLE,
                        "cannot write the archive: " + e.getMessage());
            }
            // 8. Check it (R10 step 2).
            check(tmp, password, workDir);
            // A manual export or import that started meanwhile may have changed the files while
            // they were read, e.g. an old cert with a new key: keep nothing, record nothing (R14).
            if (interference.happened()) {
                Log.i(TAG, "A manual export or import started during the backup; discarding it");
                if (!tmp.delete()) {
                    Log.w(TAG, "Cannot delete " + tmp);
                }
                tmp = null;
                return Outcome.INTERRUPTED;
            }
            // 9. Keep it (R10 step 3).
            if (!tmp.renameTo(target)) {
                throw new StepFailed(FailureReason.FOLDER_NOT_WRITABLE, "cannot rename " + tmp + " to " + target);
            }
            tmp = null;
            Log.i(TAG, "Wrote automatic backup " + target);
            // 10. Retention (R10 step 4, R11); never deletes the backup just written.
            boolean pruned = BackupFolder.prune(folder, now, target);
            // 11. Record it, once it is certain the backup is still there.
            if (!target.isFile()) {
                throw new StepFailed(FailureReason.FOLDER_NOT_WRITABLE, target + " vanished after retention");
            }
            AutoBackupPrefs.recordSuccess(prefs, now, hash);
            if (!pruned) {
                Log.w(TAG, "Could not remove every old backup in " + folder);
                AutoBackupPrefs.recordFailure(prefs, now, FailureReason.RETENTION_FAILED);
            }
            return Outcome.WRITTEN;
        } catch (StepFailed e) {
            Log.w(TAG, "Automatic backup failed (" + e.reason + "): " + e.getMessage());
            if (tmp != null && tmp.exists() && !tmp.delete()) {
                Log.w(TAG, "Cannot delete " + tmp);
            }
            AutoBackupPrefs.recordFailure(prefs, now, e.reason);
            return Outcome.FAILED;
        } finally {
            ConfigImporter.deleteQuietly(workDir);
        }
    }

    /** config.xml and the device keys, then the HTTPS pair only when both files exist. */
    private List<File> includedFiles() {
        List<File> files = new ArrayList<>();
        files.add(new File(filesDir, Constants.CONFIG_FILE));
        files.add(new File(filesDir, Constants.PUBLIC_KEY_FILE));
        files.add(new File(filesDir, Constants.PRIVATE_KEY_FILE));
        File httpsCert = new File(filesDir, Constants.HTTPS_CERT_FILE);
        File httpsKey = new File(filesDir, Constants.HTTPS_KEY_FILE);
        if (httpsCert.isFile() && httpsKey.isFile()) {
            files.add(httpsCert);
            files.add(httpsKey);
        }
        return files;
    }

    /** The archive opens with the password, holds the required entries, and its config parses. */
    private static void check(File archive, String password, File workDir) throws StepFailed {
        ConfigArchive.Check result = ConfigArchive.check(archive, password, Arrays.asList(REQUIRED_ENTRIES), workDir);
        if (result != ConfigArchive.Check.OK) {
            throw new StepFailed(FailureReason.CHECK_FAILED, "archive check: " + result);
        }
        File checkDir = new File(workDir, "check");
        try {
            UnzipParameters unzip = new UnzipParameters();
            unzip.setExtractSymbolicLinks(false);
            new ZipFile(archive, password.toCharArray())
                    .extractFile(Constants.CONFIG_FILE, checkDir.getAbsolutePath(), unzip);
        } catch (IOException e) {
            throw new StepFailed(FailureReason.CHECK_FAILED, "cannot extract config.xml: " + e.getMessage());
        }
        List<String> problems = ConfigImporter.configProblems(new File(checkDir, Constants.CONFIG_FILE));
        if (!problems.isEmpty()) {
            throw new StepFailed(FailureReason.CHECK_FAILED, "config.xml: " + problems);
        }
    }
}
