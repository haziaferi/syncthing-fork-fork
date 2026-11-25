package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.ConfigArchive;
import com.nutomic.syncthingandroid.util.ConfigImporter;
import com.nutomic.syncthingandroid.util.ConfigImporterTest;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class AutoBackupWriterTest {

    /** 2025-11-23T12:13:20Z. */
    private static final long T0 = 1763900000000L;
    private static final long MINUTE = 60 * 1000L;

    private static final AutoBackupWriter.ZipWriter REAL = ConfigArchive::writeZip;

    /** Writes the real archive, then cuts it to half its length. */
    private static final AutoBackupWriter.ZipWriter TRUNCATING = (target, paths, password) -> {
        ConfigArchive.writeZip(target, paths, password);
        try (RandomAccessFile f = new RandomAccessFile(target, "rw")) {
            f.setLength(f.length() / 2);
        }
    };

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File filesDir;
    private File storage;
    private File defaultFolder;
    private SharedPreferences prefs;
    private final long[] now = {T0};
    private final Clock clock = () -> now[0];

    @Before
    public void setUp() throws Exception {
        filesDir = tmp.newFolder("files");
        storage = tmp.newFolder("storage");
        defaultFolder = new File(storage, AutoBackupPrefs.DEFAULT_REL_FOLDER);
        write(new File(filesDir, Constants.CONFIG_FILE), ConfigImporterTest.VALID_CONFIG);
        write(new File(filesDir, Constants.PUBLIC_KEY_FILE), "CERT");
        write(new File(filesDir, Constants.PRIVATE_KEY_FILE), "KEY");
        write(new File(filesDir, Constants.INDEX_DB_FOLDER + "/main.db"), "DB");
        prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        prefs.edit().clear().commit();
        prefs.edit()
                .putString(Constants.PREF_BACKUP_PASSWORD, "pw")
                .putBoolean(Constants.PREF_RUN_ON_WIFI, false)
                .commit();
        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isTrue();
    }

    private static void write(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private AutoBackupWriter writer(AutoBackupWriter.ZipWriter zipWriter) {
        return writer(true, zipWriter);
    }

    private AutoBackupWriter writer(boolean storageAccess, AutoBackupWriter.ZipWriter zipWriter) {
        return new AutoBackupWriter(filesDir, storage, prefs, clock, storageAccess, zipWriter);
    }

    /** Runs a backup one minute after the previous one. */
    private AutoBackupWriter.Outcome run(AutoBackupWriter.ZipWriter zipWriter) {
        now[0] += MINUTE;
        return writer(zipWriter).run();
    }

    private void changeSetting(String value) {
        prefs.edit().putString("some_setting", value).commit();
    }

    private static TreeSet<String> entries(File zip) throws Exception {
        TreeSet<String> names = new TreeSet<>();
        for (FileHeader h : new ZipFile(zip, "pw".toCharArray()).getFileHeaders()) {
            names.add(h.getFileName());
        }
        return names;
    }

    private static Map<String, byte[]> contents(File folder) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                result.put(f.getName(), Files.readAllBytes(f.toPath()));
            }
        }
        return result;
    }

    private static void assertSameContents(Map<String, byte[]> before, File folder) throws IOException {
        Map<String, byte[]> after = contents(folder);
        assertThat(after.keySet()).isEqualTo(before.keySet());
        for (String name : before.keySet()) {
            assertThat(after.get(name)).isEqualTo(before.get(name));
        }
    }

    private AutoBackupPrefs.FailureReason lastReason() {
        AutoBackupPrefs.Failure failure = AutoBackupPrefs.lastFailure(prefs);
        assertThat(failure).isNotNull();
        return failure.reason;
    }

    private void assertNoTmp(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                assertThat(f.getName()).doesNotContain(BackupFolder.TMP_SUFFIX);
            }
        }
    }

    // SPEC:auto-config-backup:R03
    @Test
    public void run_archiveHoldsExactlyTheIncludedFiles() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(entries(BackupFolder.list(defaultFolder).get(0))).containsExactly(
                Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE,
                Constants.SHARED_PREFS_FILE);

        write(new File(filesDir, Constants.HTTPS_CERT_FILE), "HCERT");
        write(new File(filesDir, Constants.HTTPS_KEY_FILE), "HKEY");
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(entries(BackupFolder.list(defaultFolder).get(0))).containsExactly(
                Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE,
                Constants.HTTPS_CERT_FILE, Constants.HTTPS_KEY_FILE, Constants.SHARED_PREFS_FILE);

        assertThat(new File(filesDir, Constants.HTTPS_KEY_FILE).delete()).isTrue();
        changeSetting("only-cert");
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(entries(BackupFolder.list(defaultFolder).get(0))).hasSize(4);
        assertThat(entries(BackupFolder.list(defaultFolder).get(0))).doesNotContain(Constants.HTTPS_CERT_FILE);
    }

    // SPEC:auto-config-backup:R04
    @Test
    public void run_backupStagesThroughImport() throws Exception {
        prefs.edit().putString(AutoBackupPrefs.KEY_LAST_HASH, "old").commit();
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        File backup = BackupFolder.list(defaultFolder).get(0);

        ConfigImporter.Staged staged = ConfigImporter.stage(backup, "pw", tmp.newFolder("other"));

        assertThat(staged.prefs).isNotNull();
        assertThat(staged.prefs.get(Constants.PREF_RUN_ON_WIFI)).isEqualTo(false);
        for (String key : AutoBackupPrefs.STATUS_KEYS) {
            assertThat(staged.prefs).doesNotContainKey(key);
        }
    }

    // SPEC:auto-config-backup:R05
    @Test
    public void run_backupNeedsThePassword() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        File backup = BackupFolder.list(defaultFolder).get(0);

        ZipFile zip = new ZipFile(backup);
        assertThat(zip.isEncrypted()).isTrue();
        for (FileHeader h : zip.getFileHeaders()) {
            assertThat(h.getEncryptionMethod()).isEqualTo(EncryptionMethod.AES);
            assertThat(h.getAesExtraDataRecord().getAesKeyStrength()).isEqualTo(AesKeyStrength.KEY_STRENGTH_256);
        }
        File out = tmp.newFolder("out");
        assertThrows(ZipException.class, () -> new ZipFile(backup).extractAll(out.getAbsolutePath()));
        assertThat(ConfigArchive.check(backup, "pw", Arrays.asList(AutoBackupWriter.REQUIRED_ENTRIES),
                tmp.newFolder("scratch"))).isEqualTo(ConfigArchive.Check.OK);
    }

    // SPEC:auto-config-backup:R09
    @Test
    public void run_writesIntoConfiguredFolder() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(BackupFolder.list(defaultFolder)).hasSize(1);

        prefs.edit().putString(AutoBackupPrefs.KEY_REL_FOLDER, "elsewhere").commit();
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(BackupFolder.list(new File(storage, "elsewhere"))).hasSize(1);
        assertThat(BackupFolder.list(defaultFolder)).hasSize(1);
    }

    // SPEC:auto-config-backup:R10
    @Test
    public void run_truncatedArchive_keepsExistingBackups() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        Map<String, byte[]> before = contents(defaultFolder);
        changeSetting("x");

        assertThat(run(TRUNCATING)).isEqualTo(AutoBackupWriter.Outcome.FAILED);

        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.CHECK_FAILED);
        assertSameContents(before, defaultFolder);
        assertNoTmp(defaultFolder);
    }

    // SPEC:auto-config-backup:R10
    @Test
    public void run_malformedConfig_keepsExistingBackups() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        Map<String, byte[]> before = contents(defaultFolder);
        write(new File(filesDir, Constants.CONFIG_FILE), "<configuration>");

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.FAILED);

        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.CHECK_FAILED);
        assertSameContents(before, defaultFolder);
        assertNoTmp(defaultFolder);
    }

    // SPEC:auto-config-backup:R11
    @Test
    public void run_withSixBackups_leavesFiveAndOwnFilesOnly() throws Exception {
        assertThat(defaultFolder.mkdirs()).isTrue();
        for (int i = 1; i <= 6; i++) {
            assertThat(new File(defaultFolder, "config-auto-2025010" + i + "T000000Z.zip").createNewFile()).isTrue();
        }
        File oldTmp = new File(defaultFolder, "config-auto-20250101T000000Z.zip.tmp");
        assertThat(oldTmp.createNewFile()).isTrue();
        assertThat(oldTmp.setLastModified(T0 - 25 * 60 * MINUTE)).isTrue();
        File manual = new File(defaultFolder, "config.zip");
        write(manual, "manual");

        Map<String, byte[]> before = contents(defaultFolder);
        assertThat(run(TRUNCATING)).isEqualTo(AutoBackupWriter.Outcome.FAILED);
        assertSameContents(before, defaultFolder);

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        List<File> backups = BackupFolder.list(defaultFolder);
        assertThat(backups).hasSize(5);
        assertThat(backups.get(0).getName()).isEqualTo(BackupFolder.PREFIX + BackupFolder.stamp(now[0]) + ".zip");
        assertThat(oldTmp.exists()).isFalse();
        assertThat(Files.readAllBytes(manual.toPath())).isEqualTo("manual".getBytes(StandardCharsets.UTF_8));
    }

    // SPEC:auto-config-backup:R12
    @Test
    public void run_wallClockSetBackAnHour_newBackupIsNewestAndKept() throws Exception {
        for (int i = 0; i < BackupFolder.KEEP; i++) {
            changeSetting("before-" + i);
            assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        }
        now[0] -= 60 * MINUTE;
        changeSetting("after clock change");

        assertThat(writer(REAL).run()).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);

        List<File> backups = BackupFolder.list(defaultFolder);
        assertThat(backups).hasSize(BackupFolder.KEEP);
        File newest = backups.get(0);
        // The newest backup holds the changed setting, so the hash recorded for it is right.
        ConfigImporter.Staged staged = ConfigImporter.stage(newest, "pw", tmp.newFolder("other"));
        assertThat(staged.prefs.get("some_setting")).isEqualTo("after clock change");

        now[0] += MINUTE;
        assertThat(writer(REAL).run()).isEqualTo(AutoBackupWriter.Outcome.SKIPPED_UNCHANGED);
    }

    // SPEC:auto-config-backup:R13
    @Test
    public void run_unchangedContent_writesNothing() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        Map<String, byte[]> before = contents(defaultFolder);
        prefs.edit().putLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, 99L).commit();

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.SKIPPED_UNCHANGED);
        assertSameContents(before, defaultFolder);

        for (File f : BackupFolder.list(defaultFolder)) {
            assertThat(f.delete()).isTrue();
        }
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        assertThat(BackupFolder.list(defaultFolder)).hasSize(1);
    }

    // SPEC:auto-config-backup:R13
    @Test
    public void run_failureRows_recordReasonAndKeepBackups() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        Map<String, byte[]> before = contents(defaultFolder);

        write(new File(storage, "afile"), "not a folder");
        prefs.edit().putString(AutoBackupPrefs.KEY_REL_FOLDER, "afile").commit();
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.FAILED);
        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.FOLDER_NOT_WRITABLE);
        prefs.edit().remove(AutoBackupPrefs.KEY_REL_FOLDER).commit();

        changeSetting("1");
        now[0] += MINUTE;
        assertThat(writer(false, REAL).run()).isEqualTo(AutoBackupWriter.Outcome.FAILED);
        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.NO_STORAGE_ACCESS);

        changeSetting("2");
        assertThat(run((target, paths, password) -> {
            throw new IOException("write failed: ENOSPC (No space left on device)");
        })).isEqualTo(AutoBackupWriter.Outcome.FAILED);
        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.OUT_OF_SPACE);
        assertNoTmp(defaultFolder);

        changeSetting("3");
        assertThat(run(TRUNCATING)).isEqualTo(AutoBackupWriter.Outcome.FAILED);
        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.CHECK_FAILED);

        assertSameContents(before, defaultFolder);
    }

    // SPEC:auto-config-backup:R14
    @Test
    public void run_manualOperationDuringRun_keepsNothingAndRecordsNothing() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        Map<String, byte[]> before = contents(defaultFolder);
        String hashBefore = prefs.getString(AutoBackupPrefs.KEY_LAST_HASH, null);
        changeSetting("during import");
        now[0] += MINUTE;

        AutoBackupWriter interrupted = new AutoBackupWriter(filesDir, storage, prefs, clock, true, REAL,
                () -> true);
        assertThat(interrupted.run()).isEqualTo(AutoBackupWriter.Outcome.INTERRUPTED);

        assertSameContents(before, defaultFolder);
        assertThat(AutoBackupPrefs.lastFailure(prefs)).isNull();
        assertThat(prefs.getString(AutoBackupPrefs.KEY_LAST_HASH, null)).isEqualTo(hashBefore);
    }

    // SPEC:auto-config-backup:R19
    @Test
    public void run_emptyPassword_turnsFeatureOff() {
        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "").commit();

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.FAILED);

        assertThat(lastReason()).isEqualTo(AutoBackupPrefs.FailureReason.PASSWORD_REMOVED);
        assertThat(AutoBackupPrefs.isEnabled(prefs)).isFalse();
        assertThat(BackupFolder.list(defaultFolder)).isEmpty();
    }

    // SPEC:auto-config-backup:R19
    @Test
    public void run_changedPassword_newBackupUsesNewPassword() throws Exception {
        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        File older = BackupFolder.list(defaultFolder).get(0);
        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "pw2").commit();
        changeSetting("after");

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);
        File newer = BackupFolder.list(defaultFolder).get(0);
        assertThat(newer).isNotEqualTo(older);

        List<String> required = Arrays.asList(AutoBackupWriter.REQUIRED_ENTRIES);
        assertThat(ConfigArchive.check(newer, "pw2", required, tmp.newFolder("s1"))).isEqualTo(ConfigArchive.Check.OK);
        assertThat(ConfigArchive.check(newer, "pw", required, tmp.newFolder("s2"))).isNotEqualTo(ConfigArchive.Check.OK);
        assertThat(ConfigArchive.check(older, "pw", required, tmp.newFolder("s3"))).isEqualTo(ConfigArchive.Check.OK);
    }

    @Test
    public void run_concurrentManualExportFile_isLeftAlone() throws Exception {
        File manualPrefs = new File(filesDir, Constants.SHARED_PREFS_FILE);
        write(manualPrefs, "manual export in progress");

        assertThat(run(REAL)).isEqualTo(AutoBackupWriter.Outcome.WRITTEN);

        assertThat(new String(Files.readAllBytes(manualPrefs.toPath()), StandardCharsets.UTF_8))
                .isEqualTo("manual export in progress");
        assertThat(new File(filesDir, AutoBackupWriter.WORK_DIR).exists()).isFalse();
    }

    @Test
    public void isOutOfSpace_matchesErrnoAndMessage() {
        assertThat(AutoBackupWriter.isOutOfSpace(new IOException("write failed: ENOSPC"))).isTrue();
        assertThat(AutoBackupWriter.isOutOfSpace(new IOException("No space left on device"))).isTrue();
        assertThat(AutoBackupWriter.isOutOfSpace(new IOException("Permission denied"))).isFalse();
        assertThat(AutoBackupWriter.isOutOfSpace(new IOException())).isFalse();
    }
}
