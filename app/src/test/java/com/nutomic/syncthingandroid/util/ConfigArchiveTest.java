package com.nutomic.syncthingandroid.util;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.ConfigArchive.Check;

import net.lingala.zip4j.ZipFile;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class ConfigArchiveTest {

    private static final List<String> REQUIRED = Arrays.asList(
            Constants.CONFIG_FILE, Constants.PRIVATE_KEY_FILE, Constants.PUBLIC_KEY_FILE);

    private static final List<String> DROPPED_KEYS = Arrays.asList(
            // Deprecated.
            "first_start", "advanced_folder_picker", "backup_folder_name", "bind_network",
            "log_to_file", "notification_type", "notify_crashes", "suggest_new_folder_root",
            "use_legacy_hashing", "pref_current_language", "restartOnWakeup",
            "wakelock_while_binary_running", "use_root", "important_news_shown_version",
            // Cache.
            Constants.PREF_APP_START_COUNTER, Constants.PREF_BTNSTATE_FORCE_START_STOP,
            Constants.PREF_DEBUG_FACILITIES_AVAILABLE, Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
            Constants.PREF_LAST_BINARY_VERSION, Constants.PREF_LOCAL_DEVICE_ID,
            Constants.PREF_LAST_RUN_TIME);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File source;
    private File zip;
    private File scratch;

    @Before
    public void setUp() throws Exception {
        source = tmp.newFolder("files");
        write(new File(source, Constants.CONFIG_FILE), "<configuration/>");
        write(new File(source, Constants.PRIVATE_KEY_FILE), "private");
        write(new File(source, Constants.PUBLIC_KEY_FILE), "public");
        File index = new File(source, "index-v2");
        write(new File(new File(index, "sub"), "000001.log"), "db");
        zip = new File(tmp.getRoot(), "backup/syncthing-config.zip");
        scratch = tmp.newFolder("cache");
    }

    private static void write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private List<File> includes() {
        return Arrays.asList(
                new File(source, Constants.CONFIG_FILE),
                new File(source, Constants.PRIVATE_KEY_FILE),
                new File(source, Constants.PUBLIC_KEY_FILE),
                new File(source, Constants.HTTPS_CERT_FILE),   // missing: skipped
                new File(source, "index-v2"));
    }

    // Zip handling.

    @Test
    public void writeZip_thenCheck_ok_withAndWithoutPassword() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "");
        assertThat(ConfigArchive.check(zip, "", REQUIRED, scratch)).isEqualTo(Check.OK);

        ConfigArchive.writeZip(zip, includes(), "s3cret");
        assertThat(new ZipFile(zip).isEncrypted()).isTrue();
        assertThat(ConfigArchive.check(zip, "s3cret", REQUIRED, scratch)).isEqualTo(Check.OK);
    }

    @Test
    public void writeZip_twice_overwritesInsteadOfAppending() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "");
        int first = new ZipFile(zip).getFileHeaders().size();
        ConfigArchive.writeZip(zip, includes(), "");
        assertThat(new ZipFile(zip).getFileHeaders().size()).isEqualTo(first);
    }

    @Test
    public void writeZip_includesIndexDbFolderRecursively_skipsMissingPaths() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "");
        ZipFile z = new ZipFile(zip);
        assertThat(z.getFileHeader("index-v2/sub/000001.log")).isNotNull();
        assertThat(z.getFileHeader(Constants.HTTPS_CERT_FILE)).isNull();
    }

    @Test
    public void check_missingZip() {
        assertThat(ConfigArchive.check(zip, "", REQUIRED, scratch)).isEqualTo(Check.MISSING_ZIP);
    }

    @Test
    public void check_wrongPassword_cannotOpen() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "right");
        assertThat(ConfigArchive.check(zip, "wrong", REQUIRED, scratch)).isEqualTo(Check.CANNOT_OPEN);
    }

    @Test
    public void check_unencryptedZipWithPasswordSet() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "");
        assertThat(ConfigArchive.check(zip, "pw", REQUIRED, scratch))
                .isEqualTo(Check.NOT_ENCRYPTED_BUT_PASSWORD_SET);
    }

    @Test
    public void check_missingKeyPem() throws Exception {
        zip.getParentFile().mkdirs();
        new File(source, Constants.PRIVATE_KEY_FILE).delete();
        ConfigArchive.writeZip(zip, includes(), "");
        assertThat(ConfigArchive.check(zip, "", REQUIRED, scratch)).isEqualTo(Check.MISSING_REQUIRED_ENTRY);
    }

    @Test
    public void check_neverWritesOutsideScratchDir() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "");
        Set<String> before = new HashSet<>(Arrays.asList(tmp.getRoot().list()));
        ConfigArchive.check(zip, "", REQUIRED, scratch);
        assertThat(new HashSet<>(Arrays.asList(tmp.getRoot().list()))).isEqualTo(before);
        assertThat(scratch.list()).isEmpty();
    }

    @Test
    public void extractAll_restoresFiles() throws Exception {
        zip.getParentFile().mkdirs();
        ConfigArchive.writeZip(zip, includes(), "pw");
        File dest = tmp.newFolder("restore");
        ConfigArchive.extractAll(zip, "pw", dest);
        assertThat(new String(Files.readAllBytes(new File(dest, Constants.PRIVATE_KEY_FILE).toPath()),
                StandardCharsets.UTF_8)).isEqualTo("private");
        assertThat(new File(dest, "index-v2/sub/000001.log").exists()).isTrue();
    }

    // Prefs handling.

    private SharedPreferences realPrefs() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().clear().commit();
        return prefs;
    }

    private Map<String, ?> sampleExport() {
        SharedPreferences prefs = realPrefs();
        prefs.edit()
                .putBoolean("run_on_wifi", true)
                .putString(Constants.PREF_SLEEP_INTERVAL_MINUTES, "60")
                .putInt("some_int", 7)
                .putFloat("some_float", 1.5f)
                .putLong("some_long", 1234567890123L)
                .putStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>(Arrays.asList("\"Home\"", "\"Work\"")))
                .commit();
        return prefs.getAll();
    }

    @Test
    public void prefs_roundTrip_preservesAllSupportedTypes() throws Exception {
        Map<String, ?> exported = sampleExport();
        File file = tmp.newFile("sharedpreferences.dat");
        ConfigArchive.writePrefs(exported, file);
        assertThat(ConfigArchive.readPrefs(file)).isEqualTo(exported);
    }

    // Class allow-list when reading prefs.

    private File serialise(Object object) throws Exception {
        File file = tmp.newFile();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(file))) {
            out.writeObject(object);
        }
        return file;
    }

    @Test
    public void readPrefs_rejectsClassOutsideAllowList() throws Exception {
        Map<String, Object> withList = new HashMap<>();
        withList.put("list", new java.util.ArrayList<>(Arrays.asList("a")));
        File listFile = serialise(withList);
        try {
            ConfigArchive.readPrefs(listFile);
            throw new AssertionError("ArrayList value was accepted");
        } catch (java.io.InvalidClassException expected) {
            assertThat(expected.getMessage()).contains("java.util.ArrayList");
        }

        File dateFile = serialise(new Date());
        try {
            ConfigArchive.readPrefs(dateFile);
            throw new AssertionError("Date was accepted");
        } catch (java.io.InvalidClassException expected) {
            assertThat(expected.getMessage()).contains("java.util.Date");
        }
    }

    @Test
    public void readPrefs_rejectsNonMapTopLevel() throws Exception {
        File file = serialise(new HashSet<>(Arrays.asList("a")));
        try {
            ConfigArchive.readPrefs(file);
            throw new AssertionError("Top-level set was accepted");
        } catch (java.io.InvalidClassException expected) {
            // Rejected.
        }
    }

    @Test
    public void readPrefs_dropsNonStringKeys() throws Exception {
        Map<Object, Object> raw = new HashMap<>();
        raw.put(5, "x");
        raw.put(null, "n");
        raw.put("ok", "y");
        Map<String, Object> read = ConfigArchive.readPrefs(serialise(raw));
        assertThat(ConfigArchive.importablePrefs(read).keySet()).containsExactly("ok");
        assertThat(read.keySet()).containsExactly("ok");
    }

    @Test
    public void readPrefs_acceptsFileWrittenByWritePrefs() throws Exception {
        Map<String, ?> exported = sampleExport();
        File file = tmp.newFile();
        ConfigArchive.writePrefs(exported, file);
        assertThat(ConfigArchive.readPrefs(file)).isEqualTo(exported);
    }

    @Test
    public void import_rejectedPrefsFile_leavesCurrentPrefsUntouched() throws Exception {
        SharedPreferences prefs = realPrefs();
        prefs.edit().putString("current", "kept").commit();
        Map<String, Object> crafted = new HashMap<>();
        crafted.put("evil", new java.util.ArrayList<>());
        File file = serialise(crafted);

        // Same order as SyncthingService.importConfigSharedPrefs: read first, then apply.
        try {
            Map<String, Object> raw = ConfigArchive.readPrefs(file);
            ConfigArchive.applyPrefs(prefs.edit(), ConfigArchive.importablePrefs(raw), "", "");
            throw new AssertionError("Crafted file was accepted");
        } catch (java.io.InvalidClassException expected) {
            // Rejected before applyPrefs ran.
        }
        assertThat(prefs.getString("current", null)).isEqualTo("kept");
    }

    @Test
    public void importablePrefs_dropsEveryDeprecatedAndCacheKey() {
        Map<String, Object> raw = new HashMap<>();
        for (String key : DROPPED_KEYS) {
            raw.put(key, "x");
        }
        raw.put("run_on_wifi", true);
        Map<String, Object> importable = ConfigArchive.importablePrefs(raw);
        assertThat(DROPPED_KEYS).hasSize(21);
        assertThat(importable.keySet()).containsExactly("run_on_wifi");
    }

    @Test
    public void importablePrefs_dropsUnknownValueType() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("date", new Date());
        raw.put("mixed_set", new HashSet<Object>(Arrays.asList("a", 1)));
        raw.put("ok", "yes");
        assertThat(ConfigArchive.importablePrefs(raw).keySet()).containsExactly("ok");
    }

    @Test
    public void applyPrefs_clearsOldKeys_andKeepsBackupPathAndPassword() {
        SharedPreferences prefs = realPrefs();
        prefs.edit().putString("stale", "old").commit();
        Map<String, Object> importable = new HashMap<>();
        importable.put("run_on_wifi", false);
        importable.put(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>(Arrays.asList("\"Home\"")));
        importable.put(Constants.PREF_BACKUP_PASSWORD, "from-backup");

        assertThat(ConfigArchive.applyPrefs(prefs.edit(), importable, "keep/path", "keep-pw")).isTrue();

        assertThat(prefs.contains("stale")).isFalse();
        assertThat(prefs.getBoolean("run_on_wifi", true)).isFalse();
        assertThat(prefs.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, null)).containsExactly("\"Home\"");
        assertThat(prefs.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, null)).isEqualTo("keep/path");
        assertThat(prefs.getString(Constants.PREF_BACKUP_PASSWORD, null)).isEqualTo("keep-pw");
    }
}
