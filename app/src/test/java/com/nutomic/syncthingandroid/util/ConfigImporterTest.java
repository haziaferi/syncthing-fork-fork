package com.nutomic.syncthingandroid.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.backup.AutoBackupPrefs;
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

    public static final String VALID_CONFIG =
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

    // Commit and rollback.

    /** A mover whose listed calls (1-based) fail; all others rename. */
    private static ConfigImporter.Mover failingOn(int... failOn) {
        int[] calls = {0};
        return (from, to) -> {
            int call = ++calls[0];
            for (int f : failOn) {
                if (f == call) {
                    return false;
                }
            }
            return from.renameTo(to);
        };
    }

    /** Creates a symbolic link, or skips the test where the host can't (e.g. Windows without the privilege). */
    private static void symlinkOrSkip(File link, File target) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
        } catch (Exception e) {
            org.junit.Assume.assumeNoException("host can't create symbolic links", e);
        }
    }

    @Test
    public void stage_symlinkEntry_isNeverFollowed() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        Map<String, String> before = snapshot(filesDir);
        writeLiveSet(source, "new", VALID_CONFIG);
        File link = new File(source, "x");
        symlinkOrSkip(link, new File(".."));
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setSymbolicLinkAction(net.lingala.zip4j.model.ZipParameters.SymbolicLinkAction.INCLUDE_LINK_ONLY);
        net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(zip);
        for (String name : THREE) {
            z.addFile(new File(source, name));
        }
        z.addFile(link, params);

        try {
            ConfigImporter.Staged staged = ConfigImporter.stage(zip, "", filesDir);
            assertThat(ConfigImporter.isSymbolicLink(new File(staged.dir, "x"))).isFalse();
            ConfigImporter.deleteQuietly(staged.dir);
        } catch (ConfigImporter.ImportException rejected) {
            // Rejecting the archive is fine too; what matters is the live files below.
        }
        Map<String, String> after = snapshot(filesDir);
        assertThat(after).isEqualTo(before);
    }

    @Test
    public void deleteQuietly_deletesALinkWithoutFollowingIt() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        Map<String, String> before = snapshot(filesDir);
        File dir = tmp.newFolder("tree");
        write(new File(dir, "own.txt"), "x");
        symlinkOrSkip(new File(dir, "link"), filesDir);

        ConfigImporter.deleteQuietly(dir);

        assertThat(dir.exists()).isFalse();
        assertThat(snapshot(filesDir)).isEqualTo(before);
    }

    @Test
    public void stage_guiNestedDeeper_rejected() throws Exception {
        writeLiveSet(source, "new", "<configuration><x><gui/></x><options/></configuration>");
        archive(null, THREE);
        assertThrows(ConfigImporter.ImportException.class, () -> ConfigImporter.stage(zip, "", filesDir));
    }

    @Test
    public void commit_restoreFails_keepsRollbackAndNextStartFinishesIt() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG.replace("<apikey>k</apikey>", "<apikey>live</apikey>"));
        prefs.edit().putString("kept", "yes").commit();
        Map<String, String> before = snapshot(filesDir);
        ConfigImporter.Staged staged = stageFull(null);

        // Call 3 (moving cert.pem aside) fails, then call 4 (restoring config.xml) fails too.
        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, prefs, failingOn(3, 4)));
        assertThat(e.getMessage()).contains("retried");
        assertThat(ConfigImporter.isRollbackPending(filesDir)).isTrue();

        assertThat(ConfigImporter.recoverInterruptedImport(filesDir, prefs)).isTrue();
        assertThat(ConfigImporter.isRollbackPending(filesDir)).isFalse();

        assertThat(snapshot(filesDir)).isEqualTo(before);
        assertThat(prefs.getAll()).containsExactly("kept", "yes");
    }

    @Test
    public void commit_refusesWhileAnEarlierRollbackIsPending() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        write(new File(filesDir, ConfigImporter.ROLLBACK_DIR + "/" + Constants.PRIVATE_KEY_FILE), "key-saved");
        write(new File(filesDir, ConfigImporter.ROLLBACK_DIR + "/" + ConfigImporter.COMMIT_MARKER), "");
        ConfigImporter.Staged staged = stageFull(null);

        assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, prefs, RENAME));

        assertThat(new File(filesDir, ConfigImporter.ROLLBACK_DIR + "/" + Constants.PRIVATE_KEY_FILE).exists()).isTrue();
    }

    @Test
    public void commit_success_thenRecover_changesNothing() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        ConfigImporter.Staged staged = stageFull(null);
        ConfigImporter.commit(staged, filesDir, prefs, RENAME);
        Map<String, String> afterImport = snapshot(filesDir);
        Map<String, ?> prefsAfterImport = new HashMap<>(prefs.getAll());

        assertThat(ConfigImporter.recoverInterruptedImport(filesDir, prefs)).isFalse();

        assertThat(snapshot(filesDir)).isEqualTo(afterImport);
        assertThat(prefs.getAll()).isEqualTo(prefsAfterImport);
    }

    @Test
    public void commit_prefsRestoreAlsoFails_keepsRollbackForRetry() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        prefs.edit().putString("kept", "yes").commit();
        Map<String, Object> exported = new HashMap<>();
        exported.put("run_on_wifi", false);
        ConfigImporter.Staged staged = stageFull(exported);

        ConfigImporter.ImportException e = assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, new CommitFailingPrefs(prefs, Integer.MAX_VALUE), RENAME));

        assertThat(e.getMessage()).contains("retried");
        File rollback = new File(filesDir, ConfigImporter.ROLLBACK_DIR);
        assertThat(new File(rollback, ConfigImporter.COMMIT_MARKER).exists()).isTrue();
        assertThat(new File(rollback, ConfigImporter.PREFS_SNAPSHOT).exists()).isTrue();
    }

    private static final ConfigImporter.Mover RENAME = File::renameTo;

    private ConfigImporter.Staged stageFull(Map<String, ?> prefsMap) throws Exception {
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(prefsMap, ALL);
        return ConfigImporter.stage(zip, "", filesDir);
    }

    @Test
    public void commit_fullExport_replacesEveryLiveItemAndPrefs() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        prefs.edit()
                .putString("stale", "old")
                .putString(Constants.PREF_BACKUP_PASSWORD, "pw")
                .putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, "current/config.zip")
                .commit();
        Map<String, Object> exported = new HashMap<>();
        exported.put("run_on_wifi", false);
        exported.put(Constants.PREF_BACKUP_PASSWORD, "from-backup");
        exported.put(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, "from/backup.zip");
        ConfigImporter.Staged staged = stageFull(exported);

        ConfigImporter.commit(staged, filesDir, prefs, RENAME);

        Map<String, String> after = snapshot(filesDir);
        assertThat(after.get(Constants.PUBLIC_KEY_FILE)).isEqualTo("cert-new");
        assertThat(after.get(Constants.HTTPS_KEY_FILE)).isEqualTo("https-key-new");
        assertThat(after.get(Constants.INDEX_DB_FOLDER + "/main.db")).isEqualTo("db-new");
        assertThat(prefs.contains("stale")).isFalse();
        assertThat(prefs.getBoolean("run_on_wifi", true)).isFalse();
        assertThat(prefs.getString(Constants.PREF_BACKUP_PASSWORD, null)).isEqualTo("pw");
        assertThat(prefs.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, null)).isEqualTo("current/config.zip");
    }

    // SPEC:R16
    @Test
    public void commit_keepsDeviceBackupStatus() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        prefs.edit()
                .putLong(AutoBackupPrefs.KEY_LAST_SUCCESS, 5L)
                .putString(AutoBackupPrefs.KEY_LAST_HASH, "device")
                .commit();
        Map<String, Object> exported = new HashMap<>();
        exported.put(AutoBackupPrefs.KEY_LAST_SUCCESS, 9L);
        exported.put(AutoBackupPrefs.KEY_LAST_HASH, "archive");
        exported.put(AutoBackupPrefs.KEY_LAST_FAILURE, "1;CHECK_FAILED");
        exported.put("run_on_wifi", false);
        ConfigImporter.Staged staged = stageFull(exported);

        ConfigImporter.commit(staged, filesDir, prefs, RENAME);

        assertThat(prefs.getLong(AutoBackupPrefs.KEY_LAST_SUCCESS, 0L)).isEqualTo(5L);
        assertThat(prefs.getString(AutoBackupPrefs.KEY_LAST_HASH, null)).isEqualTo("device");
        assertThat(prefs.contains(AutoBackupPrefs.KEY_LAST_FAILURE)).isFalse();
        assertThat(prefs.getBoolean("run_on_wifi", true)).isFalse();
    }

    @Test
    public void configProblems_validAndMalformed() throws Exception {
        File config = new File(source, Constants.CONFIG_FILE);
        write(config, VALID_CONFIG);
        assertThat(ConfigImporter.configProblems(config)).isEmpty();
        write(config, "<configuration>");
        assertThat(ConfigImporter.configProblems(config)).hasSize(1);
    }

    @Test
    public void commit_threeFileArchive_keepsHttpsPairAndPrefs_removesLocalDeviceId() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        prefs.edit().putString("kept", "yes").putString(Constants.PREF_LOCAL_DEVICE_ID, "OLD-ID").commit();
        writeLiveSet(source, "new", VALID_CONFIG);
        archive(null, THREE);
        ConfigImporter.Staged staged = ConfigImporter.stage(zip, "", filesDir);

        ConfigImporter.commit(staged, filesDir, prefs, RENAME);

        Map<String, String> after = snapshot(filesDir);
        assertThat(after.get(Constants.PRIVATE_KEY_FILE)).isEqualTo("key-new");
        assertThat(after.get(Constants.HTTPS_CERT_FILE)).isEqualTo("https-cert-live");
        assertThat(after.get(Constants.HTTPS_KEY_FILE)).isEqualTo("https-key-live");
        assertThat(after).doesNotContainKey(Constants.INDEX_DB_FOLDER + "/main.db");
        assertThat(prefs.getString("kept", null)).isEqualTo("yes");
        assertThat(prefs.contains(Constants.PREF_LOCAL_DEVICE_ID)).isFalse();
    }

    @Test
    public void commit_failsOnThirdMove_restoresEveryLiveFileByteForByte() throws Exception {
        // A live config distinct from the imported one, so a config left swapped is detected.
        writeLiveSet(filesDir, "live", VALID_CONFIG.replace("<apikey>k</apikey>", "<apikey>live</apikey>"));
        prefs.edit().putString("kept", "yes").commit();
        Map<String, String> before = snapshot(filesDir);
        Map<String, Object> exported = new HashMap<>();
        exported.put("run_on_wifi", false);
        ConfigImporter.Staged staged = stageFull(exported);

        assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, prefs, failingOn(3)));

        assertThat(snapshot(filesDir)).isEqualTo(before);
        assertThat(prefs.getAll()).containsExactly("kept", "yes");
    }

    @Test
    public void commit_failsWhileApplyingPrefs_restoresFilesAndPrefs() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        prefs.edit().putString("kept", "yes").commit();
        Map<String, String> before = snapshot(filesDir);
        Map<String, Object> exported = new HashMap<>();
        exported.put("run_on_wifi", false);
        ConfigImporter.Staged staged = stageFull(exported);

        assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, new CommitFailingPrefs(prefs), RENAME));

        assertThat(snapshot(filesDir)).isEqualTo(before);
        assertThat(prefs.getAll()).containsExactly("kept", "yes");
    }

    @Test
    public void commit_success_leavesNoStagingOrRollbackDir() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        ConfigImporter.Staged staged = stageFull(null);

        ConfigImporter.commit(staged, filesDir, prefs, RENAME);

        assertThat(new File(filesDir, ConfigImporter.STAGING_DIR).exists()).isFalse();
        assertThat(new File(filesDir, ConfigImporter.ROLLBACK_DIR).exists()).isFalse();
    }

    @Test
    public void commit_noPreviousHttpsPair_thenFailure_removesTheNewPair() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        new File(filesDir, Constants.HTTPS_CERT_FILE).delete();
        new File(filesDir, Constants.HTTPS_KEY_FILE).delete();
        Map<String, String> before = snapshot(filesDir);
        ConfigImporter.Staged staged = stageFull(null);

        // config out+in, cert out+in, key out+in, https-cert in, https-key in, index out: fail on the index move-in.
        assertThrows(ConfigImporter.ImportException.class,
                () -> ConfigImporter.commit(staged, filesDir, prefs, failingOn(10)));

        assertThat(snapshot(filesDir)).isEqualTo(before);
    }

    @Test
    public void recover_withMarker_restoresRollbackItemsAndPrefs() throws Exception {
        // State after a crash while the commit was moving items: config and cert were swapped,
        // the key had only been moved out, and a new https-cert was moved into an empty slot.
        File rollback = new File(filesDir, ConfigImporter.ROLLBACK_DIR);
        write(new File(rollback, Constants.CONFIG_FILE), "config-old");
        write(new File(rollback, Constants.PUBLIC_KEY_FILE), "cert-old");
        write(new File(rollback, Constants.PRIVATE_KEY_FILE), "key-old");
        write(new File(rollback, Constants.HTTPS_CERT_FILE + ConfigImporter.ABSENT_SUFFIX), "");
        Map<String, Object> snapshotPrefs = new HashMap<>();
        snapshotPrefs.put("kept", "yes");
        ConfigArchive.writePrefs(snapshotPrefs, new File(rollback, ConfigImporter.PREFS_SNAPSHOT));
        write(new File(rollback, ConfigImporter.COMMIT_MARKER), "");
        write(new File(filesDir, Constants.CONFIG_FILE), "config-new");
        write(new File(filesDir, Constants.PUBLIC_KEY_FILE), "cert-new");
        write(new File(filesDir, Constants.HTTPS_CERT_FILE), "https-cert-new");
        write(new File(filesDir, Constants.HTTPS_KEY_FILE), "https-key-untouched");
        write(new File(filesDir, ConfigImporter.STAGING_DIR + "/key.pem"), "key-new");
        prefs.edit().putString("half", "applied").commit();

        assertThat(ConfigImporter.recoverInterruptedImport(filesDir, prefs)).isTrue();

        Map<String, String> after = snapshot(filesDir);
        assertThat(after).containsExactly(
                Constants.CONFIG_FILE, "config-old",
                Constants.PUBLIC_KEY_FILE, "cert-old",
                Constants.PRIVATE_KEY_FILE, "key-old",
                Constants.HTTPS_KEY_FILE, "https-key-untouched");
        assertThat(prefs.getAll()).containsExactly("kept", "yes");
    }

    @Test
    public void recover_withoutMarker_deletesStaleDirs_returnsFalse() throws Exception {
        writeLiveSet(filesDir, "live", VALID_CONFIG);
        write(new File(filesDir, ConfigImporter.ROLLBACK_DIR + "/config.xml"), "stale");
        write(new File(filesDir, ConfigImporter.STAGING_DIR + "/config.xml"), "stale");
        Map<String, String> live = snapshot(filesDir);
        live.keySet().removeIf(k -> k.startsWith("import-"));

        assertThat(ConfigImporter.recoverInterruptedImport(filesDir, prefs)).isFalse();

        assertThat(snapshot(filesDir)).isEqualTo(live);
    }

    /**
     * Delegates to real prefs, but the first {@code failures} editor commits fail without
     * writing anything. Later editors are the real ones.
     */
    private static final class CommitFailingPrefs implements SharedPreferences {
        private final SharedPreferences real;
        private int failuresLeft;

        CommitFailingPrefs(SharedPreferences real) {
            this(real, 1);
        }

        CommitFailingPrefs(SharedPreferences real, int failures) {
            this.real = real;
            this.failuresLeft = failures;
        }

        @Override public Map<String, ?> getAll() { return real.getAll(); }
        @Override public String getString(String k, String d) { return real.getString(k, d); }
        @Override public java.util.Set<String> getStringSet(String k, java.util.Set<String> d) { return real.getStringSet(k, d); }
        @Override public int getInt(String k, int d) { return real.getInt(k, d); }
        @Override public long getLong(String k, long d) { return real.getLong(k, d); }
        @Override public float getFloat(String k, float d) { return real.getFloat(k, d); }
        @Override public boolean getBoolean(String k, boolean d) { return real.getBoolean(k, d); }
        @Override public boolean contains(String k) { return real.contains(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        @Override
        public Editor edit() {
            if (failuresLeft <= 0) {
                return real.edit();
            }
            failuresLeft--;
            return new Editor() {
                @Override public Editor putString(String k, String v) { return this; }
                @Override public Editor putStringSet(String k, java.util.Set<String> v) { return this; }
                @Override public Editor putInt(String k, int v) { return this; }
                @Override public Editor putLong(String k, long v) { return this; }
                @Override public Editor putFloat(String k, float v) { return this; }
                @Override public Editor putBoolean(String k, boolean v) { return this; }
                @Override public Editor remove(String k) { return this; }
                @Override public Editor clear() { return this; }
                @Override public boolean commit() { return false; }
                @Override public void apply() { }
            };
        }
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
