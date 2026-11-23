package com.nutomic.syncthingandroid.util;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.service.Constants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Imports a config archive all-or-nothing: the archive is extracted into a staging folder and
 * checked completely before any live file changes. Holds no Context, so it can be tested on
 * the JVM. Stopping and starting Syncthing is the caller's job.
 */
public final class ConfigImporter {

    private static final String TAG = "ConfigImporter";

    public static final String STAGING_DIR = "import-staging";
    public static final String ROLLBACK_DIR = "import-rollback";
    public static final String COMMIT_MARKER = "COMMIT_IN_PROGRESS";
    /** Prefs as they were before the commit, inside the rollback folder. */
    public static final String PREFS_SNAPSHOT = "prefs-snapshot.dat";
    /** Suffix of a rollback entry recording that a live item did not exist before the commit. */
    public static final String ABSENT_SUFFIX = ".absent";

    /** An archive that passed every check, extracted into {@link #dir}. */
    public static final class Staged {
        public final File dir;
        public final boolean hasHttpsPair;
        public final boolean hasIndex;
        /** The archive's prefs, already read through the allow-list; null if it has none. */
        @Nullable public final Map<String, Object> prefs;

        Staged(File dir, boolean hasHttpsPair, boolean hasIndex, @Nullable Map<String, Object> prefs) {
            this.dir = dir;
            this.hasHttpsPair = hasHttpsPair;
            this.hasIndex = hasIndex;
            this.prefs = prefs;
        }
    }

    /** An import that was refused or undone; {@link #problems} lists every reason. */
    public static final class ImportException extends Exception {
        public final List<String> problems;

        ImportException(List<String> problems) {
            super(String.join("; ", problems));
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        ImportException(String problem) {
            this(Collections.singletonList(problem));
        }
    }

    private ConfigImporter() {
    }

    /**
     * Extracts the archive into a fresh {@code filesDir/import-staging} and checks it.
     * Live files are never touched. On failure the staging folder is removed and every
     * problem found is reported at once.
     */
    public static Staged stage(File zip, String password, File filesDir) throws ImportException {
        File stagingDir = new File(filesDir, STAGING_DIR);
        deleteQuietly(stagingDir);
        if (!stagingDir.mkdirs()) {
            throw new ImportException("Cannot create staging folder " + stagingDir);
        }
        try {
            ConfigArchive.extractAll(zip, password, stagingDir);
        } catch (IOException e) {
            deleteQuietly(stagingDir);
            throw new ImportException("Cannot extract the archive: " + e.getMessage());
        }

        List<String> problems = new ArrayList<>();
        checkContainment(stagingDir, stagingDir, problems);
        for (String required : new String[]{Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE}) {
            if (!new File(stagingDir, required).isFile()) {
                problems.add("Missing " + required);
            }
        }
        File config = new File(stagingDir, Constants.CONFIG_FILE);
        if (config.isFile()) {
            checkConfig(config, problems);
        }
        boolean hasHttpsCert = new File(stagingDir, Constants.HTTPS_CERT_FILE).isFile();
        boolean hasHttpsKey = new File(stagingDir, Constants.HTTPS_KEY_FILE).isFile();
        if (hasHttpsCert != hasHttpsKey) {
            problems.add("Incomplete HTTPS pair: missing "
                    + (hasHttpsCert ? Constants.HTTPS_KEY_FILE : Constants.HTTPS_CERT_FILE));
        }
        Map<String, Object> prefs = null;
        File prefsFile = new File(stagingDir, Constants.SHARED_PREFS_FILE);
        if (prefsFile.isFile()) {
            try {
                prefs = ConfigArchive.readPrefs(prefsFile);
            } catch (IOException | ClassNotFoundException e) {
                problems.add("Unreadable " + Constants.SHARED_PREFS_FILE + ": " + e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            deleteQuietly(stagingDir);
            throw new ImportException(problems);
        }
        boolean hasIndex = new File(stagingDir, Constants.INDEX_DB_FOLDER).isDirectory();
        return new Staged(stagingDir, hasHttpsCert, hasIndex, prefs);
    }

    /** Adds a problem for every extracted path that resolves outside the staging folder. */
    private static void checkContainment(File stagingDir, File current, List<String> problems) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        try {
            String root = stagingDir.getCanonicalPath() + File.separator;
            for (File child : children) {
                if (!child.getCanonicalPath().startsWith(root)) {
                    problems.add("Archive entry escapes the staging folder: " + child.getName());
                } else if (child.isDirectory()) {
                    checkContainment(stagingDir, child, problems);
                }
            }
        } catch (IOException e) {
            problems.add("Cannot resolve extracted paths: " + e.getMessage());
        }
    }

    /** Checks config.xml parses and has the elements the app relies on. */
    private static void checkConfig(File config, List<String> problems) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (ParserConfigurationException e) {
                // Android's parser doesn't support this feature; it also doesn't load external entities.
                Log.v(TAG, "Parser without disallow-doctype-decl support");
            }
            factory.setExpandEntityReferences(false);
            doc = factory.newDocumentBuilder().parse(config);
        } catch (Exception e) {
            problems.add(Constants.CONFIG_FILE + " is not valid XML: " + e.getMessage());
            return;
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"configuration".equals(root.getTagName())) {
            problems.add(Constants.CONFIG_FILE + " has no <configuration> root element");
            return;
        }
        if (root.getElementsByTagName("gui").getLength() == 0) {
            problems.add(Constants.CONFIG_FILE + " has no <gui> element");
        }
        if (root.getElementsByTagName("options").getLength() == 0) {
            problems.add(Constants.CONFIG_FILE + " has no <options> element");
        }
    }

    /** Moves a file or folder; injectable so tests can fail a chosen move. */
    public interface Mover {
        boolean move(File from, File to);
    }

    /**
     * Moves each live item aside into {@code filesDir/import-rollback} and the staged item into
     * its place, then applies the prefs. If anything fails, every moved item is put back and
     * the prefs are restored, so the live configuration is as before.
     */
    public static void commit(Staged staged, File filesDir, SharedPreferences prefs, Mover mover)
            throws ImportException {
        Map<String, ?> prefsBefore = new HashMap<>(prefs.getAll());
        File rollbackDir = new File(filesDir, ROLLBACK_DIR);
        deleteQuietly(rollbackDir);
        try {
            if (!rollbackDir.mkdirs()) {
                throw new IOException("Cannot create " + rollbackDir);
            }
            // The marker is written last, so it never exists without the prefs snapshot.
            ConfigArchive.writePrefs(prefsBefore, new File(rollbackDir, PREFS_SNAPSHOT));
            writeEmpty(new File(rollbackDir, COMMIT_MARKER));
        } catch (IOException e) {
            deleteQuietly(rollbackDir);
            deleteQuietly(staged.dir);
            throw new ImportException("Cannot prepare the rollback folder: " + e.getMessage());
        }

        List<String> moved = new ArrayList<>();
        String failure = null;
        for (String name : itemsToReplace(staged)) {
            File live = new File(filesDir, name);
            File stagedItem = new File(staged.dir, name);
            if (live.exists()) {
                if (!mover.move(live, new File(rollbackDir, name))) {
                    failure = "Cannot move " + name + " aside";
                    break;
                }
            } else {
                try {
                    writeEmpty(new File(rollbackDir, name + ABSENT_SUFFIX));
                } catch (IOException e) {
                    failure = "Cannot record absent " + name + ": " + e.getMessage();
                    break;
                }
            }
            moved.add(name);
            if (stagedItem.exists() && !mover.move(stagedItem, live)) {
                failure = "Cannot move the imported " + name + " into place";
                break;
            }
        }

        if (failure == null) {
            boolean prefsOk;
            if (staged.prefs != null) {
                String relPathToZip = prefs.getString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, "");
                String backupPassword = prefs.getString(Constants.PREF_BACKUP_PASSWORD, "");
                prefsOk = ConfigArchive.applyPrefs(prefs.edit(), ConfigArchive.importablePrefs(staged.prefs),
                        relPathToZip, backupPassword);
            } else {
                prefsOk = prefs.edit().remove(Constants.PREF_LOCAL_DEVICE_ID).commit();
            }
            if (!prefsOk) {
                failure = "Cannot save the imported settings";
            }
        }

        if (failure != null) {
            Log.e(TAG, "Import failed, rolling back: " + failure);
            Collections.reverse(moved);
            for (String name : moved) {
                restoreItem(filesDir, rollbackDir, name);
            }
            if (!ConfigArchive.restorePrefs(prefs, prefsBefore)) {
                Log.e(TAG, "Failed to restore the previous settings");
            }
            deleteQuietly(rollbackDir);
            deleteQuietly(staged.dir);
            throw new ImportException(failure);
        }

        new File(rollbackDir, COMMIT_MARKER).delete();
        deleteQuietly(rollbackDir);
        deleteQuietly(staged.dir);
    }

    /**
     * Finishes the rollback of an import interrupted by a crash. Call before anything reads
     * config.xml. Returns true if an interrupted import was rolled back.
     */
    public static boolean recoverInterruptedImport(File filesDir, SharedPreferences prefs) {
        File rollbackDir = new File(filesDir, ROLLBACK_DIR);
        File stagingDir = new File(filesDir, STAGING_DIR);
        boolean interrupted = new File(rollbackDir, COMMIT_MARKER).exists();
        if (interrupted) {
            Log.w(TAG, "Rolling back an import that was interrupted");
            File[] items = rollbackDir.listFiles();
            if (items != null) {
                for (File item : items) {
                    String name = item.getName();
                    if (name.equals(COMMIT_MARKER) || name.equals(PREFS_SNAPSHOT)) {
                        continue;
                    }
                    restoreItem(filesDir, rollbackDir,
                            name.endsWith(ABSENT_SUFFIX) ? name.substring(0, name.length() - ABSENT_SUFFIX.length()) : name);
                }
            }
            try {
                ConfigArchive.restorePrefs(prefs, ConfigArchive.readPrefs(new File(rollbackDir, PREFS_SNAPSHOT)));
            } catch (IOException | ClassNotFoundException e) {
                Log.e(TAG, "Cannot restore the settings snapshot", e);
            }
        }
        deleteQuietly(rollbackDir);
        deleteQuietly(stagingDir);
        return interrupted;
    }

    /** Live items an import replaces, in commit order. */
    private static List<String> itemsToReplace(Staged staged) {
        List<String> names = new ArrayList<>(Arrays.asList(
                Constants.CONFIG_FILE, Constants.PUBLIC_KEY_FILE, Constants.PRIVATE_KEY_FILE));
        if (staged.hasHttpsPair) {
            names.add(Constants.HTTPS_CERT_FILE);
            names.add(Constants.HTTPS_KEY_FILE);
        }
        // The index is always moved out: Syncthing rebuilds it if the archive has none.
        names.add(Constants.INDEX_DB_FOLDER);
        return names;
    }

    /** Puts {@code name} back as it was before the commit touched it. */
    private static void restoreItem(File filesDir, File rollbackDir, String name) {
        File live = new File(filesDir, name);
        File saved = new File(rollbackDir, name);
        File absent = new File(rollbackDir, name + ABSENT_SUFFIX);
        if (!saved.exists() && !absent.exists()) {
            return;  // Never touched.
        }
        deleteQuietly(live);
        if (saved.exists() && !saved.renameTo(live)) {
            Log.e(TAG, "Failed to restore " + name);
        }
    }

    private static void writeEmpty(File file) throws IOException {
        if (!file.createNewFile() && !file.isFile()) {
            throw new IOException("Cannot create " + file);
        }
    }

    static void deleteQuietly(File file) {
        try {
            FileUtils.deleteDirectoryRecursively(file);
        } catch (IOException e) {
            Log.w(TAG, "Failed to delete " + file, e);
        }
    }
}
