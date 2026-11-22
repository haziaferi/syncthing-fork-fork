package com.nutomic.syncthingandroid.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.nutomic.syncthingandroid.TestHosts;
import com.nutomic.syncthingandroid.service.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 37)
public class ConfigXmlSaveTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        assumeFalse(TestHosts.NEEDS_POSIX_RENAME, TestHosts.isWindowsHost());
    }

    @Test
    public void saveChanges_tempFileNotWritable_keepsConfigByteForByte() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        File configFile = Constants.getConfigFile(ctx);
        byte[] before = Files.readAllBytes(configFile.toPath());

        // A leftover temp file the app cannot write to makes the next save fail.
        File temp = Constants.getConfigTempFile(ctx);
        Files.write(temp.toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
        assertThat(temp.setWritable(false)).isTrue();
        // Root ignores file permissions, so the write would not fail there.
        assumeFalse("host user can write read-only files (root?)", temp.canWrite());

        config.saveChanges();

        assertThat(Files.readAllBytes(configFile.toPath())).isEqualTo(before);
        assertThat(temp.exists()).isFalse();
    }

    @Test
    public void saveChanges_success_replacesConfigAndLeavesNoTemp() throws Exception {
        ConfigXml config = ConfigXmlTestSupport.loadFixture(ctx, "basic.xml");
        File configFile = Constants.getConfigFile(ctx);
        config.setFolderPause("a", true);
        config.saveChanges();

        String saved = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        assertThat(saved).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(saved).contains("<paused>true</paused>");
        assertThat(Constants.getConfigTempFile(ctx).exists()).isFalse();
    }
}
