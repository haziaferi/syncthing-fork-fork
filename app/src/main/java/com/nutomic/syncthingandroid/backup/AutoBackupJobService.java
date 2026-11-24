package com.nutomic.syncthingandroid.backup;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.nutomic.syncthingandroid.util.ConfigArchive;

/** Runs {@link AutoBackupJob} on a background thread when the scheduler fires. */
public class AutoBackupJobService extends JobService {

    private static final String TAG = "AutoBackupJobService";

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                AutoBackupJob.Result result =
                        AutoBackupJob.execute(getApplicationContext(), Clock.SYSTEM, ConfigArchive::writeZip);
                Log.i(TAG, "Automatic backup: " + result);
            } catch (RuntimeException e) {
                Log.e(TAG, "Automatic backup crashed", e);
            } finally {
                jobFinished(params, false);
            }
        }, TAG).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
