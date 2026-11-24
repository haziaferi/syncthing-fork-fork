package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class AutoBackupPrefsTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        prefs.edit().clear().commit();
    }

    // SPEC:R15
    @Test
    public void isEnabled_freshPrefs_isFalse() {
        assertThat(AutoBackupPrefs.isEnabled(prefs)).isFalse();
    }

    // SPEC:R05
    @Test
    public void setEnabled_withoutPassword_isRefused() {
        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isFalse();
        assertThat(prefs.contains(AutoBackupPrefs.KEY_ENABLED)).isFalse();

        prefs.edit().putString(Constants.PREF_BACKUP_PASSWORD, "pw").commit();
        assertThat(AutoBackupPrefs.setEnabled(prefs, true)).isTrue();
        assertThat(AutoBackupPrefs.isEnabled(prefs)).isTrue();
    }

    // SPEC:R22
    @Test
    public void isRuntimeKey_coversEveryRuntimeKey() {
        String[] runtime = {
                Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
                Constants.PREF_LAST_RUN_TIME,
                Constants.PREF_APP_START_COUNTER,
                Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + "ABC-123",
                Constants.PREF_DEBUG_FACILITIES_AVAILABLE,
                Constants.PREF_LAST_BINARY_VERSION,
                Constants.PREF_BTNSTATE_FORCE_START_STOP,
                AutoBackupPrefs.KEY_LAST_SUCCESS,
                AutoBackupPrefs.KEY_LAST_FAILURE,
                AutoBackupPrefs.KEY_LAST_HASH,
        };
        for (String key : runtime) {
            assertWithMessage(key).that(AutoBackupPrefs.isRuntimeKey(key)).isTrue();
        }
        String[] user = {
                Constants.PREF_RUN_ON_WIFI,
                Constants.PREF_BACKUP_PASSWORD,
                AutoBackupPrefs.KEY_ENABLED,
                AutoBackupPrefs.KEY_REL_FOLDER,
        };
        for (String key : user) {
            assertWithMessage(key).that(AutoBackupPrefs.isRuntimeKey(key)).isFalse();
        }
        assertThat(AutoBackupPrefs.isRuntimeKey(null)).isFalse();
    }

    // SPEC:R09
    @Test
    public void backupFolder_defaultAndChanged() {
        File root = new File("root");
        assertThat(AutoBackupPrefs.backupFolder(prefs, root))
                .isEqualTo(new File(root, "backups/syncthing/auto"));

        prefs.edit().putString(AutoBackupPrefs.KEY_REL_FOLDER, "x/y").commit();
        assertThat(AutoBackupPrefs.backupFolder(prefs, root)).isEqualTo(new File(root, "x/y"));

        prefs.edit().putString(AutoBackupPrefs.KEY_REL_FOLDER, "").commit();
        assertThat(AutoBackupPrefs.backupFolder(prefs, root))
                .isEqualTo(new File(root, AutoBackupPrefs.DEFAULT_REL_FOLDER));
    }

    @Test
    public void recordFailure_roundTrips() {
        AutoBackupPrefs.recordFailure(prefs, 1234L, AutoBackupPrefs.FailureReason.OUT_OF_SPACE);
        AutoBackupPrefs.Failure failure = AutoBackupPrefs.lastFailure(prefs);
        assertThat(failure).isNotNull();
        assertThat(failure.timeMillis).isEqualTo(1234L);
        assertThat(failure.reason).isEqualTo(AutoBackupPrefs.FailureReason.OUT_OF_SPACE);

        prefs.edit().putString(AutoBackupPrefs.KEY_LAST_FAILURE, "1234;NO_SUCH_REASON").commit();
        assertThat(AutoBackupPrefs.lastFailure(prefs)).isNull();
        prefs.edit().putString(AutoBackupPrefs.KEY_LAST_FAILURE, "garbage").commit();
        assertThat(AutoBackupPrefs.lastFailure(prefs)).isNull();
    }
}
