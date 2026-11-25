package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class ContentHashTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private List<File> files;

    @Before
    public void setUp() throws Exception {
        File config = tmp.newFile("config.xml");
        Files.write(config.toPath(), "<configuration/>".getBytes(StandardCharsets.UTF_8));
        File cert = tmp.newFile("cert.pem");
        Files.write(cert.toPath(), "CERT".getBytes(StandardCharsets.UTF_8));
        files = Arrays.asList(config, cert);
    }

    private static Map<String, Object> prefs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_on_wifi", true);
        m.put("backup_password", "pw");
        m.put("count", 3);
        m.put("set", new HashSet<>(Arrays.asList("b", "a")));
        return m;
    }

    @Test
    public void of_sameContent_sameHash() throws Exception {
        Map<String, Object> forward = prefs();
        Map<String, Object> reverse = new LinkedHashMap<>();
        String[] keys = forward.keySet().toArray(new String[0]);
        for (int i = keys.length - 1; i >= 0; i--) {
            reverse.put(keys[i], forward.get(keys[i]));
        }
        String hash = ContentHash.of(files, forward);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(ContentHash.of(files, reverse)).isEqualTo(hash);
    }

    /** Every key of R22's table, with a device ID or folder ID where the key takes one. */
    static final String[] RUNTIME_KEYS = {
            Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
            Constants.PREF_LAST_RUN_TIME,
            Constants.PREF_APP_START_COUNTER,
            Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + "X",
            Constants.PREF_DEBUG_FACILITIES_AVAILABLE,
            Constants.PREF_LAST_BINARY_VERSION,
            Constants.PREF_BTNSTATE_FORCE_START_STOP,
            "previously_selected_syncthing_folder",
            "saved_sub_directory_abcd-1234",
            AutoBackupPrefs.KEY_LAST_SUCCESS,
            AutoBackupPrefs.KEY_LAST_FAILURE,
            AutoBackupPrefs.KEY_LAST_HASH,
    };

    // SPEC:auto-config-backup:R22
    @Test
    public void of_runtimeKeyChange_sameHash() throws Exception {
        String hash = ContentHash.of(files, prefs());
        for (String key : RUNTIME_KEYS) {
            Map<String, Object> added = prefs();
            added.put(key, "first");
            assertWithMessage(key).that(ContentHash.of(files, added)).isEqualTo(hash);
            added.put(key, "second");
            assertWithMessage(key).that(ContentHash.of(files, added)).isEqualTo(hash);
        }
    }

    @Test
    public void of_userSettingOrFileChange_newHash() throws Exception {
        String hash = ContentHash.of(files, prefs());

        Map<String, Object> wifi = prefs();
        wifi.put("run_on_wifi", false);
        assertThat(ContentHash.of(files, wifi)).isNotEqualTo(hash);

        Map<String, Object> typed = prefs();
        typed.put("run_on_wifi", "true");
        assertThat(ContentHash.of(files, typed)).isNotEqualTo(hash);

        Files.write(files.get(0).toPath(), "<configuratioN/>".getBytes(StandardCharsets.UTF_8));
        assertThat(ContentHash.of(files, prefs())).isNotEqualTo(hash);
    }
}
