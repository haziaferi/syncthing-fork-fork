package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.JobUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class AutoBackupSchedulerTest {

    /** 2025-11-23T12:13:20Z. */
    static final long T0 = 1763900000000L;
    static final long MINUTE = 60 * 1000L;

    private Context context;
    private SharedPreferences prefs;
    private JobScheduler jobs;
    private final long[] now = {T0};
    private final Clock clock = () -> now[0];

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
        context.getSharedPreferences(AutoBackupScheduler.STATE_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        jobs = context.getSystemService(JobScheduler.class);
        jobs.cancelAll();
        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "pw").commit();
        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isTrue();
        AutoBackupScheduler.cancel(context);
    }

    static JobInfo pendingBackup(JobScheduler jobs) {
        JobInfo found = null;
        for (JobInfo job : jobs.getAllPendingJobs()) {
            if (job.getId() == AutoBackupScheduler.JOB_ID) {
                assertThat(found).isNull();
                found = job;
            }
        }
        return found;
    }

    private void changeAt(long minutes) {
        now[0] = T0 + minutes * MINUTE;
        AutoBackupScheduler.onPreferenceChanged(context, prefs, Constants.PREF_RUN_ON_WIFI, clock);
    }

    // SPEC:R13
    @Test
    public void threeChangesWithinTenMinutes_scheduleOneJob() {
        changeAt(0);
        changeAt(3);
        changeAt(6);

        JobInfo job = pendingBackup(jobs);
        assertThat(job).isNotNull();
        assertThat(job.getMinLatencyMillis()).isEqualTo(AutoBackupScheduler.DEBOUNCE_MS);
        assertThat(job.isPersisted()).isTrue();
    }

    // SPEC:R13
    @Test
    public void changeEveryFiveMinutes_runsWithinSixtyMinutes() {
        for (long minutes = 0; minutes <= 55; minutes += 5) {
            changeAt(minutes);
            JobInfo job = pendingBackup(jobs);
            assertThat(job).isNotNull();
            long runsAt = now[0] + job.getMinLatencyMillis();
            assertThat(runsAt).isAtMost(T0 + 60 * MINUTE);
            if (minutes == 55) {
                assertThat(runsAt).isEqualTo(T0 + 60 * MINUTE);
            }
        }
    }

    @Test
    public void delayMillis_neverNegative() {
        assertThat(AutoBackupScheduler.delayMillis(T0, T0)).isEqualTo(AutoBackupScheduler.DEBOUNCE_MS);
        assertThat(AutoBackupScheduler.delayMillis(T0 + 90 * MINUTE, T0)).isEqualTo(0L);
    }

    // SPEC:R15
    @Test
    public void featureOff_changeSchedulesNothing() {
        AutoBackupPrefs.setEnabled(prefs, false);
        AutoBackupScheduler.onPreferenceChanged(context, prefs, AutoBackupPrefs.KEY_ENABLED, clock);
        changeAt(0);
        assertThat(pendingBackup(jobs)).isNull();

        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isTrue();
        AutoBackupScheduler.onPreferenceChanged(context, prefs, AutoBackupPrefs.KEY_ENABLED, clock);
        assertThat(pendingBackup(jobs)).isNotNull();
    }

    // SPEC:R22
    @Test
    public void runtimeKeyChange_schedulesNothing() {
        String[] runtime = {
                Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
                Constants.PREF_LAST_RUN_TIME,
                Constants.PREF_APP_START_COUNTER,
                Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + "ABC-123",
                Constants.PREF_DEBUG_FACILITIES_AVAILABLE,
                Constants.PREF_LAST_BINARY_VERSION,
                Constants.PREF_BTNSTATE_FORCE_START_STOP,
                "previously_selected_syncthing_folder",
                "saved_sub_directory_abcd-1234",
                AutoBackupPrefs.KEY_LAST_SUCCESS,
                AutoBackupPrefs.KEY_LAST_FAILURE,
                AutoBackupPrefs.KEY_LAST_HASH,
        };
        for (String key : runtime) {
            prefs.edit().putString(key, "x").commit();
            AutoBackupScheduler.onPreferenceChanged(context, prefs, key, clock);
            assertThat(pendingBackup(jobs)).isNull();
        }

        prefs.edit().putBoolean(Constants.PREF_RUN_ON_WIFI, true).commit();
        AutoBackupScheduler.onPreferenceChanged(context, prefs, Constants.PREF_RUN_ON_WIFI, clock);
        assertThat(pendingBackup(jobs)).isNotNull();
    }

    @Test
    public void clearAll_countsAsChange() {
        AutoBackupScheduler.onPreferenceChanged(context, prefs, null, clock);
        assertThat(pendingBackup(jobs)).isNotNull();
    }

    // SPEC:R19
    @Test
    public void passwordCleared_turnsFeatureOff() {
        changeAt(0);
        assertThat(pendingBackup(jobs)).isNotNull();

        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "").commit();
        AutoBackupScheduler.onPreferenceChanged(context, prefs, Constants.PREF_BACKUP_PASSWORD, clock);

        assertThat(AutoBackupPrefs.isEnabled(prefs)).isFalse();
        assertThat(pendingBackup(jobs)).isNull();
        AutoBackupPrefs.Failure failure = AutoBackupPrefs.lastFailure(prefs);
        assertThat(failure).isNotNull();
        assertThat(failure.reason).isEqualTo(AutoBackupPrefs.FailureReason.PASSWORD_REMOVED);
    }

    @Test
    public void cancelSyncTriggerJob_leavesBackupJob() {
        JobUtils.scheduleSyncTriggerServiceJob(context, 60, true);
        changeAt(0);

        JobUtils.cancelSyncTriggerJob(context);

        assertThat(pendingBackup(jobs)).isNotNull();
        for (JobInfo job : jobs.getAllPendingJobs()) {
            assertThat(job.getId()).isNotEqualTo(JobUtils.SYNC_TRIGGER_JOB_ID);
        }
    }
}
