package com.nutomic.syncthingandroid.backup;

import static com.google.common.truth.Truth.assertThat;

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

    // SPEC:R22
    @Test
    public void of_runtimeKeyChange_sameHash() throws Exception {
        Map<String, Object> base = prefs();
        String hash = ContentHash.of(files, base);

        Map<String, Object> changed = prefs();
        changed.put("last_sync_id", 42L);
        changed.put("device_lastseen_X", 1000L);
        changed.put(AutoBackupPrefs.KEY_LAST_HASH, "abc");
        assertThat(ContentHash.of(files, changed)).isEqualTo(hash);

        changed.put("last_sync_id", 43L);
        changed.put("device_lastseen_X", 2000L);
        changed.put(AutoBackupPrefs.KEY_LAST_HASH, "def");
        assertThat(ContentHash.of(files, changed)).isEqualTo(hash);
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
