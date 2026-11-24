package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.ConfigArchive;
import com.nutomic.syncthingandroid.util.ConfigImporter;
import com.nutomic.syncthingandroid.util.ConfigImporterTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class AutoBackupJobTest {

    private static final long T0 = AutoBackupSchedulerTest.T0;

    private Context context;
    private SharedPreferences prefs;
    private JobScheduler jobs;
    private File backupFolder;
    private final long[] now = {T0};
    private final Clock clock = () -> now[0];

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
        context.getSharedPreferences(AutoBackupScheduler.STATE_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        jobs = context.getSystemService(JobScheduler.class);
        jobs.cancelAll();

        File filesDir = context.getFilesDir();
        write(new File(filesDir, Constants.CONFIG_FILE), ConfigImporterTest.VALID_CONFIG);
        write(new File(filesDir, Constants.PUBLIC_KEY_FILE), "CERT");
        write(new File(filesDir, Constants.PRIVATE_KEY_FILE), "KEY");
        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "pw").commit();
        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isTrue();
        backupFolder = AutoBackupPrefs.backupFolder(prefs, Environment.getExternalStorageDirectory());
        ConfigImporter.deleteQuietly(backupFolder);
    }

    @After
    public void tearDown() {
        ConfigImporter.deleteQuietly(new File(context.getFilesDir(), ConfigImporter.ROLLBACK_DIR));
        ConfigImporter.deleteQuietly(backupFolder);
    }

    private static void write(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertDeferred() {
        assertThat(AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, true))
                .isEqualTo(AutoBackupJob.Result.DEFERRED);
        assertThat(BackupFolder.list(backupFolder)).isEmpty();
        JobInfo job = AutoBackupSchedulerTest.pendingBackup(jobs);
        assertThat(job).isNotNull();
        assertThat(job.getMinLatencyMillis()).isEqualTo(AutoBackupScheduler.DEBOUNCE_MS);
        jobs.cancelAll();
    }

    // SPEC:R14
    @Test
    public void pendingRollback_writesNothingAndReschedules() throws Exception {
        write(new File(new File(context.getFilesDir(), ConfigImporter.ROLLBACK_DIR), ConfigImporter.COMMIT_MARKER), "");
        assertDeferred();
        ConfigImporter.deleteQuietly(new File(context.getFilesDir(), ConfigImporter.ROLLBACK_DIR));

        AutoBackupGate.beginManualOperation();
        try {
            assertDeferred();
        } finally {
            AutoBackupGate.endManualOperation();
        }

        assertThat(AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, true))
                .isEqualTo(AutoBackupJob.Result.WRITTEN);
        assertThat(BackupFolder.list(backupFolder)).hasSize(1);
    }

    @Test
    public void changeDuringRun_getsItsOwnBackup() {
        long changeAt = T0 + 30 * 1000L;
        AutoBackupWriter.ZipWriter changing = (target, paths, password) -> {
            now[0] = changeAt;
            AutoBackupScheduler.noteChange(context, clock);
            ConfigArchive.writeZip(target, paths, password);
        };
        now[0] = T0 - 5 * 60 * 1000L;
        AutoBackupScheduler.noteChange(context, clock);
        now[0] = T0;
        jobs.cancelAll();

        assertThat(AutoBackupJob.execute(context, clock, changing, true)).isEqualTo(AutoBackupJob.Result.WRITTEN);

        assertThat(context.getSharedPreferences(AutoBackupScheduler.STATE_PREFS, Context.MODE_PRIVATE)
                .getLong(AutoBackupScheduler.KEY_FIRST_CHANGE, 0L)).isEqualTo(changeAt);
        assertThat(AutoBackupSchedulerTest.pendingBackup(jobs)).isNotNull();
    }

    @Test
    public void featureOff_returnsDisabledAndCancels() {
        AutoBackupScheduler.noteChange(context, clock);
        AutoBackupPrefs.setEnabled(prefs, false);

        assertThat(AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, true))
                .isEqualTo(AutoBackupJob.Result.DISABLED);
        assertThat(AutoBackupSchedulerTest.pendingBackup(jobs)).isNull();
    }
}
