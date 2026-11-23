package com.nutomic.syncthingandroid.util;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Loads config.xml fixtures from src/test/resources/config into the app's files dir. */
public final class ConfigXmlTestSupport {

    /** Local device ID used by every fixture. */
    public static final String LOCAL_DEVICE_ID =
            "AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA";

    private ConfigXmlTestSupport() {
    }

    /** Copies the fixture to {@link Constants#getConfigFile} without loading it. */
    public static File installFixture(Context ctx, String name) throws IOException {
        File target = Constants.getConfigFile(ctx);
        target.getParentFile().mkdirs();
        try (InputStream in = ConfigXmlTestSupport.class.getClassLoader()
                .getResourceAsStream("config/" + name)) {
            if (in == null) {
                throw new IOException("Missing fixture config/" + name);
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        // Cached ID, so ConfigXml never starts the Syncthing binary to ask for it.
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(Constants.PREF_LOCAL_DEVICE_ID, LOCAL_DEVICE_ID)
                .commit();
        return target;
    }

    /** Installs the fixture and calls {@link ConfigXml#loadConfig()}. */
    public static ConfigXml loadFixture(Context ctx, String name) throws IOException {
        installFixture(ctx, name);
        ConfigXml config = new ConfigXml(ctx);
        config.loadConfig();
        return config;
    }
}
