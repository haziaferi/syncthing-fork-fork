package com.nutomic.syncthingandroid;

import java.util.Locale;

/** Facts about the machine the JVM unit tests run on (not the emulated Android device). */
public final class TestHosts {

    /** Message for tests that need POSIX rename semantics (replace an existing file). */
    public static final String NEEDS_POSIX_RENAME = "rename over an existing file needs a POSIX host";

    private TestHosts() {
    }

    public static boolean isWindowsHost() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
