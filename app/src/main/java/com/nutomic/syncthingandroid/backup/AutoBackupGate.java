package com.nutomic.syncthingandroid.backup;

import com.nutomic.syncthingandroid.util.ConfigImporter;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the automatic backup back while a manual export or import runs, or while an
 * import's rollback is unfinished (spec R14).
 */
public final class AutoBackupGate {

    private static final AtomicInteger sManualOperations = new AtomicInteger();

    private AutoBackupGate() {
    }

    public static void beginManualOperation() {
        sManualOperations.incrementAndGet();
    }

    public static void endManualOperation() {
        sManualOperations.decrementAndGet();
    }

    public static boolean isBlocked(File filesDir) {
        return sManualOperations.get() > 0 || ConfigImporter.isRollbackPending(filesDir);
    }
}
