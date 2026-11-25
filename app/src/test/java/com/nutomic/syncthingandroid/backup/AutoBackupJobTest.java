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

    // SPEC:auto-config-backup:R14
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

    private long firstChangeAt() {
        return context.getSharedPreferences(AutoBackupScheduler.STATE_PREFS, Context.MODE_PRIVATE)
                .getLong(AutoBackupScheduler.KEY_FIRST_CHANGE, 0L);
    }

    @Test
    public void changeDuringRun_getsItsOwnBackup() {
        long changeAt = T0 + 30 * 1000L;
        boolean[] scheduledMidRun = {true};
        AutoBackupWriter.ZipWriter changing = (target, paths, password) -> {
            now[0] = changeAt;
            AutoBackupScheduler.noteChange(context, clock);
            // Scheduling the job's own ID while it runs would stop it on a device (M1).
            scheduledMidRun[0] = AutoBackupSchedulerTest.pendingBackup(jobs) != null;
            ConfigArchive.writeZip(target, paths, password);
        };
        now[0] = T0 - 5 * 60 * 1000L;
        AutoBackupScheduler.noteChange(context, clock);
        now[0] = T0;
        jobs.cancelAll();

        assertThat(AutoBackupJob.execute(context, clock, changing, true)).isEqualTo(AutoBackupJob.Result.WRITTEN);

        assertThat(scheduledMidRun[0]).isFalse();
        assertThat(firstChangeAt()).isEqualTo(changeAt);
        JobInfo job = AutoBackupSchedulerTest.pendingBackup(jobs);
        assertThat(job).isNotNull();
        assertThat(job.getMinLatencyMillis()).isEqualTo(AutoBackupScheduler.DEBOUNCE_MS - (now[0] - changeAt));
    }

    @Test
    public void runWhileAnotherRuns_isDeferred() {
        AutoBackupJob.Result[] nested = {null};
        AutoBackupWriter.ZipWriter overlapping = (target, paths, password) -> {
            nested[0] = AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, true);
            ConfigArchive.writeZip(target, paths, password);
        };

        assertThat(AutoBackupJob.execute(context, clock, overlapping, true)).isEqualTo(AutoBackupJob.Result.WRITTEN);

        assertThat(nested[0]).isEqualTo(AutoBackupJob.Result.DEFERRED);
        assertThat(BackupFolder.list(backupFolder)).hasSize(1);
    }

    // SPEC:auto-config-backup:R14
    @Test
    public void importStartingMidRun_keepsNothingAndReschedules() {
        long pendingSince = T0 - 5 * 60 * 1000L;
        now[0] = pendingSince;
        AutoBackupScheduler.noteChange(context, clock);
        now[0] = T0;
        jobs.cancelAll();
        AutoBackupWriter.ZipWriter importStarts = (target, paths, password) -> {
            AutoBackupGate.beginManualOperation();
            ConfigArchive.writeZip(target, paths, password);
        };
        try {
            assertThat(AutoBackupJob.execute(context, clock, importStarts, true))
                    .isEqualTo(AutoBackupJob.Result.DEFERRED);
        } finally {
            AutoBackupGate.endManualOperation();
        }

        assertThat(backupFolder.list()).isEmpty();
        assertThat(AutoBackupPrefs.lastFailure(prefs)).isNull();
        assertThat(firstChangeAt()).isEqualTo(pendingSince);
        JobInfo job = AutoBackupSchedulerTest.pendingBackup(jobs);
        assertThat(job).isNotNull();
        assertThat(job.getMinLatencyMillis()).isEqualTo(AutoBackupScheduler.DEBOUNCE_MS);
    }

    @Test
    public void failedRun_keepsThePendingChange() {
        long pendingSince = T0 - 5 * 60 * 1000L;
        now[0] = pendingSince;
        AutoBackupScheduler.noteChange(context, clock);
        now[0] = T0;
        jobs.cancelAll();

        assertThat(AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, false))
                .isEqualTo(AutoBackupJob.Result.FAILED);

        assertThat(firstChangeAt()).isEqualTo(pendingSince);
        // No retry loop: the next change, or the next start of the feature, schedules it.
        assertThat(AutoBackupSchedulerTest.pendingBackup(jobs)).isNull();

        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "").commit();
        assertThat(AutoBackupJob.execute(context, clock, ConfigArchive::writeZip, true))
                .isEqualTo(AutoBackupJob.Result.FAILED);
        assertThat(firstChangeAt()).isEqualTo(0L);
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
