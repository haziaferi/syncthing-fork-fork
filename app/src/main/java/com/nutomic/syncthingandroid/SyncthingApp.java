package com.nutomic.syncthingandroid;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.util.ConfigImporter;
import com.nutomic.syncthingandroid.util.RecentTasksController;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    private static final String TAG = "SyncthingApp";

    @Inject DaggerComponent mComponent;

    // SharedPreferences keeps weak listener references, so retain the controller for this process.
    private RecentTasksController mRecentTasksController;

    @Override
    public void onCreate() {
        super.onCreate();

        // Undo a config import a crash interrupted, before anything in the app reads config.xml.
        if (ConfigImporter.recoverInterruptedImport(getFilesDir(), PreferenceManager.getDefaultSharedPreferences(this))) {
            Log.w(TAG, "Rolled back a config import that was interrupted.");
        }

        DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build()
                .inject(this);

        mRecentTasksController = new RecentTasksController(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        mRecentTasksController.start();

        // Set VM policy to avoid crash when sending folder URI to file manager.
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(vmPolicy);

        /*
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
            StrictMode.setThreadPolicy(threadPolicy);
        }
        */
    }

    public DaggerComponent component() {
        return mComponent;
    }
}
