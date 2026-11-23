package com.nutomic.syncthingandroid.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.TestHosts;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Characterises ConfigXml as it behaves today: parsing, migration and save. */
@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class ConfigXmlTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Constants.getConfigFile(ctx).delete();
    }

    private static void assumePosix() {
        assumeFalse(TestHosts.NEEDS_POSIX_RENAME, TestHosts.isWindowsHost());
    }

    private String savedConfig() throws Exception {
        return new String(Files.readAllBytes(Constants.getConfigFile(ctx).toPath()), StandardCharsets.UTF_8);
    }

    private static Folder folder(List<Folder> folders, String id) {
        for (Folder f : folders) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        throw new AssertionError("No folder " + id);
    }

    @Test
    public void getFolders_sortedByLabelFallingBackToId_excludesDefaults() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        List<String> ids = new ArrayList<>();
        for (Folder f : config.getFolders()) {
            ids.add(f.id);
        }
        // "a" has an empty label and sorts by its id; the <defaults> folder is not listed.
        assertThat(ids).containsExactly("a", "b-id").inOrder();
    }

    @Test
    public void getDevices_excludeLocal_and_sortedByNameFallingBackToId() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        List<String> ids = new ArrayList<>();
        for (Device d : config.getDevices(false)) {
            ids.add(d.deviceID);
        }
        assertThat(ids).containsExactly(
                "CCCCCCC-CCCCCCC-CCCCCCC-CCCCCCC-CCCCCCC-CCCCCCC-CCCCCCC-CCCCCCC",
                "BBBBBBB-BBBBBBB-BBBBBBB-BBBBBBB-BBBBBBB-BBBBBBB-BBBBBBB-BBBBBBB").inOrder();
        assertThat(config.getDevices(true)).hasSize(3);
    }

    @Test
    public void loadConfig_setsIgnorePermsAndHashersOnEveryFolder() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        for (Folder f : config.getFolders()) {
            assertThat(f.ignorePerms).isTrue();
            assertThat(f.hashers).isEqualTo(1);
        }
    }

    @Test
    public void loadConfig_setsIgnorePermsAndHashersOnEveryFolder_andSaves() throws Exception {
        assumePosix();
        ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        ConfigXml reloaded = new ConfigXml(ctx);
        reloaded.loadConfig();
        assertThat(folder(reloaded.getFolders(), "b-id").ignorePerms).isTrue();
        assertThat(folder(reloaded.getFolders(), "b-id").hashers).isEqualTo(1);
        assertThat(savedConfig()).doesNotContain("ignorePerms=\"false\"");
    }

    @Test
    public void loadConfig_removesFsWatcherUnackedNotification() throws Exception {
        assumePosix();
        ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        assertThat(savedConfig()).doesNotContain("fsWatcherNotification");
    }

    @Test
    public void loadConfig_v27_enablesFsWatcherAndBumpsVersionTo28() throws Exception {
        assumePosix();
        ConfigXmlTestSupport.loadFixture(ctx, "v27.xml");
        String saved = savedConfig();
        assertThat(saved).contains("version=\"28\"");
        assertThat(saved).doesNotContain("version=\"27\"");
        assertThat(saved).contains("fsWatcherEnabled=\"true\"");
    }

    @Test
    public void unicodeLabelAndPath_surviveSaveAndReload() throws Exception {
        assumePosix();
        String expectedLabel = "사진 📷 é";
        String expectedPath = "/storage/emulated/0/사진";

        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "unicode.xml");
        Folder f = folder(config.getFolders(), "photos");
        assertThat(f.label).isEqualTo(expectedLabel);
        config.updateFolder(f);
        config.saveChanges();

        ConfigXml reloaded = new ConfigXml(ctx);
        reloaded.loadConfig();
        Folder again = folder(reloaded.getFolders(), "photos");
        assertThat(again.label.codePoints().toArray()).isEqualTo(expectedLabel.codePoints().toArray());
        assertThat(again.path).isEqualTo(expectedPath);
    }

    @Test
    public void unicodeLabel_parsedExactly() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "unicode.xml");
        assertThat(folder(config.getFolders(), "photos").label.codePoints().toArray())
                .isEqualTo("사진 📷 é".codePoints().toArray());
    }

    @Test
    public void missingFile_throwsOpenConfigException() {
        ConfigXml config = new ConfigXml(ctx);
        assertThrows(ConfigXml.OpenConfigException.class, config::loadConfig);
    }

    @Test
    public void malformedXml_throwsOpenConfigException() throws Exception {
        ConfigXmlTestSupport.installFixture(ctx, "malformed.xml");
        ConfigXml config = new ConfigXml(ctx);
        assertThrows(ConfigXml.OpenConfigException.class, config::loadConfig);
    }

    @Test
    public void getWebGuiBindPort_badAddress_returnsDefaultPort() throws Exception {
        File file = ConfigXmlTestSupport.installFixture(ctx, "basic.xml");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("<address>127.0.0.1:8384</address>", "<address>localhost</address>");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        ConfigXml config = new ConfigXml(ctx);
        config.loadConfig();
        assertThat(config.getWebGuiBindPort()).isEqualTo(Constants.DEFAULT_WEBGUI_TCP_PORT);
    }

    @Test
    public void getWebGuiBindPort_validAddress_returnsItsPort() throws Exception {
        File file = ConfigXmlTestSupport.installFixture(ctx, "basic.xml");
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("<address>127.0.0.1:8384</address>", "<address>127.0.0.1:9999</address>");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        ConfigXml config = new ConfigXml(ctx);
        config.loadConfig();
        assertThat(config.getWebGuiBindPort()).isEqualTo(9999);
    }

    @Test
    public void updateFolder_thenReload_roundTripsFields() throws Exception {
        assumePosix();
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        Folder f = folder(config.getFolders(), "b-id");
        f.label = "renamed";
        f.path = "/storage/emulated/0/elsewhere";
        f.type = Constants.FOLDER_TYPE_SEND_ONLY;
        f.fsWatcherDelayS = 42.5f;
        config.updateFolder(f);
        config.saveChanges();

        ConfigXml reloaded = new ConfigXml(ctx);
        reloaded.loadConfig();
        Folder again = folder(reloaded.getFolders(), "b-id");
        assertThat(again.label).isEqualTo("renamed");
        assertThat(again.path).isEqualTo("/storage/emulated/0/elsewhere");
        assertThat(again.type).isEqualTo(Constants.FOLDER_TYPE_SEND_ONLY);
        assertThat(again.fsWatcherDelayS).isEqualTo(42.5f);
    }
}
