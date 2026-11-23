package com.nutomic.syncthingandroid.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class ConfigImporterTest {

    static final String VALID_CONFIG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<configuration version=\"37\"><gui><address>127.0.0.1:8384</address>"
            + "<apikey>k</apikey></gui><options></options></configuration>";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File filesDir;
    private File source;
    private File zip;
    private SharedPreferences prefs;

    @Before
    public void setUp() throws Exception {
        filesDir = tmp.newFolder("files");
        source = tmp.newFolder("source");
        zip = new File(tmp.getRoot(), "config.zip");
        prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext());
        prefs.edit().clear().commit();
    }

    static void write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the six live items into {@code dir}, each with content tagged by {@code tag}. */
    static void writeLiveSet(File dir, String tag, String config) throws Exception {
        write(new File(dir, Constants.CONFIG_FILE), config);
        write(new File(dir, Constants.PUBLIC_KEY_FILE), "cert-" + tag);
        write(new File(dir, Constants.PRIVATE_KEY_FILE), "key-" + tag);
        write(new File(dir, Constants.HTTPS_CERT_FILE), "https-cert-" + tag);
        write(new File(dir, Constants.HTTPS_KEY_FILE), "https-key-" + tag);
        write(new File(dir, Constants.INDEX_DB_FOLDER + "/main.db"), "db-" + tag);
    }

    /** Zips the named items of {@link #source} (plus an optional prefs map) into {@link #zip}. */
    private void archive(Map<String, ?> prefsMap, String... names) throws Exception {
        List<File> include = new ArrayList<>();
        for (String name : names) {
            include.add(new File(source, name));
        }
        if (prefsMap != null) {
            File prefsFile = new File(source, Constants.SHARED_PREFS_FILE);
            ConfigArchive.writePrefs(prefsMap, prefsFile);
            include.add(prefsFile);
        }
        ConfigArchive.writeZip(zip, include, "");
    }

    private static final String[] ALL = {
            Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE,
            Constants.HTTPS_CERT_FILE, Constants.HTTPS_KEY_FILE, Constants.INDEX_DB_FOLDER};

    private static final String[] THREE = {
            Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE};

    /** Relative path to content for every file under {@code dir}. */
    static Map<String, String> snapshot(File dir) throws Exception {
        Map<String, String> result = new TreeMap<>();
        Files.walk(dir.toPath()).filter(Files::isRegularFile).forEach(p -> {
            try {
                result.put(dir.toPath().relativize(p).toString().replace('\\', '/'),
                        new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return result;
    }

    private List<String> problemsOf(ConfigImporter.ImportException e) {
        return e.problems;
    }

    // Staging and validation.

    @Test
    public void stage_fullExport_isValid() throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        Map<String, Object> exported = new HashMap<>();
        exported.put("run_on_wifi", false);
        archive(exported, ALL);

        ConfigImporter.Staged staged = ConfigImporter.stage(zip, "", filesDir);

        assertThat(staged.hasHttpsPair).isTrue();
        assertThat(staged.hasIndex).isTrue();
        assertThat(staged.prefs).containsExactly("run_on_wifi", false);
        assertThat(staged.dir).isEqualTo(new File(filesDir, ConfigImporter.STAGING_DIR));
    }

    @Test
    public void stage_threeFileArchive_isValid() throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(null, THREE);

        ConfigImporter.Staged staged = ConfigImporter.stage(zip, "", filesDir);

        assertThat(staged.hasHttpsPair).isFalse();
        assertThat(staged.hasIndex).isFalse();
        assertThat(staged.prefs).isNull();
    }

    @Test
    public void stage_missingCertAndKey_reportsBothInOneError() throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(null, Constants.CONFIG_FILE);

        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.stage(zip, "", filesDir));

        String all = String.join("\n", problemsOf(e));
        assertThat(all).contains(Constants.PUBLIC_KEY_FILE);
        assertThat(all).contains(Constants.PRIVATE_KEY_FILE);
        assertThat(problemsOf(e)).hasSize(2);
    }

    @Test
    public void stage_malformedConfig_rejected() throws Exception {
        writeLiveSet(source, "new", "<configuration><gui>");
        archive(null, THREE);
        assertThrows(ConfigImporter.ImportException.class, () -> ConfigImporter.stage(zip, "", filesDir));
    }

    @Test
    public void stage_configWithoutGui_rejected() throws Exception {
        writeLiveSet(source, "new", "<configuration version=\"37\"><options/></configuration>");
        archive(null, THREE);
        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.stage(zip, "", filesDir));
        assertThat(String.join("\n", problemsOf(e))).contains("<gui>");
    }

    @Test
    public void stage_halfHttpsPair_rejected() throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(null, Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE,
                Constants.HTTPS_CERT_FILE);
        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.stage(zip, "", filesDir));
        assertThat(String.join("\n", problemsOf(e))).contains(Constants.HTTPS_KEY_FILE);
    }

    @Test
    public void stage_disallowedPrefsClass_rejected() throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        Map<String, Object> crafted = new HashMap<>();
        crafted.put("evil", new ArrayList<>(Arrays.asList("x")));
        archive(crafted, THREE);
        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.stage(zip, "", filesDir));
        assertThat(String.join("\n", problemsOf(e))).contains(Constants.SHARED_PREFS_FILE);
    }

    @Test
    public void stage_entryEscapingStagingDir_rejected() throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : THREE) {
                out.putNextEntry(new ZipEntry(name));
                out.write((name.equals(Constants.CONFIG_FILE) ? VALID_CONFIG : "x").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("../escaped.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertThrows(ConfigImporter.ImportException.class, () -> ConfigImporter.stage(zip, "", filesDir));
        assertThat(new File(filesDir, "escaped.txt").exists()).isFalse();
        assertThat(new File(tmp.getRoot(), "escaped.txt").exists()).isFalse();
    }

    @Test
    public void stage_neverTouchesLiveFiles() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        Map<String, String> before = snapshot(filesDir);
        writeLiveSet(source, "new", "not xml");
        archive(null, Constants.CONFIG_FILE);

        assertThrows(ConfigImporter.ImportException.class, () -> ConfigImporter.stage(zip, "", filesDir));

        assertThat(snapshot(filesDir)).isEqualTo(before);
        assertThat(new File(filesDir, ConfigImporter.STAGING_DIR).exists()).isFalse();
    }

    @Test
    public void stage_removesStaleStagingDir() throws Exception {
        write(new File(filesDir, ConfigImporter.STAGING_DIR + "/stale.txt"), "old");
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(null, THREE);

        ConfigImporter.Staged staged = ConfigImporter.stage(zip, "", filesDir);

        assertThat(new File(staged.dir, "stale.txt").exists()).isFalse();
    }
}
