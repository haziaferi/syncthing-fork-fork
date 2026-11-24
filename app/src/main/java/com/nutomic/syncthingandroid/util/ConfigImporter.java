package com.nutomic.syncthingandroid.util;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nutomic.syncthingandroid.backup.AutoBackupPrefs;
import com.nutomic.syncthingandroid.service.Constants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
    /** A finished rollback folder is renamed to this before it is deleted. */
    public static final String TRASH_DIR = "import-rollback.trash";
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

    /** The problems {@link #checkConfig} finds in {@code config}; empty when there are none. */
    public static List<String> configProblems(File config) {
        List<String> problems = new ArrayList<>();
        checkConfig(config, problems);
        return problems;
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
        if (!hasChildElement(root, "gui")) {
            problems.add(Constants.CONFIG_FILE + " has no <gui> element");
        }
        if (!hasChildElement(root, "options")) {
            problems.add(Constants.CONFIG_FILE + " has no <options> element");
        }
    }

    private static boolean hasChildElement(Element parent, String tagName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tagName.equals(n.getNodeName())) {
                return true;
            }
        }
        return false;
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
        if (new File(rollbackDir, COMMIT_MARKER).exists()) {
            // An earlier rollback is still pending; its saved files must not be overwritten.
            deleteQuietly(staged.dir);
            throw new ImportException("An earlier import could not be rolled back completely; see the log");
        }
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
                Map<String, Object> keepStatus = new HashMap<>();
                for (String key : AutoBackupPrefs.STATUS_KEYS) {
                    if (prefsBefore.containsKey(key)) {
                        keepStatus.put(key, prefsBefore.get(key));
                    }
                }
                prefsOk = ConfigArchive.applyPrefs(prefs.edit(), ConfigArchive.importablePrefs(staged.prefs),
                        relPathToZip, backupPassword, keepStatus);
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
            boolean restored = true;
            for (String name : moved) {
                restored &= restoreItem(filesDir, rollbackDir, name, mover);
            }
            restored &= ConfigArchive.restorePrefs(prefs, prefsBefore);
            deleteQuietly(staged.dir);
            if (!restored) {
                // Keep the marker and everything saved, so the next start retries the rollback.
                throw new ImportException(failure + "; the rollback is incomplete and will be retried at the next start");
            }
            discardRollback(rollbackDir);
            throw new ImportException(failure);
        }

        discardRollback(rollbackDir);
        deleteQuietly(staged.dir);
    }

    /**
     * Retires the rollback folder of a finished commit or rollback. It is renamed first, so a
     * leftover marker can never be mistaken for an interrupted import later.
     */
    private static void discardRollback(File rollbackDir) {
        File trash = new File(rollbackDir.getParentFile(), TRASH_DIR);
        deleteQuietly(trash);
        if (rollbackDir.renameTo(trash)) {
            deleteQuietly(trash);
            return;
        }
        File marker = new File(rollbackDir, COMMIT_MARKER);
        if (marker.exists() && !marker.delete()) {
            Log.e(TAG, "Cannot retire " + rollbackDir + "; a later start may roll back this import");
            return;
        }
        deleteQuietly(rollbackDir);
    }

    /** True while a commit's rollback is unfinished; Syncthing must not start then. */
    public static boolean isRollbackPending(File filesDir) {
        return new File(new File(filesDir, ROLLBACK_DIR), COMMIT_MARKER).exists();
    }

    /**
     * Finishes the rollback of an import interrupted by a crash. Call before anything reads
     * config.xml. Returns true if an interrupted import was rolled back.
     */
    public static boolean recoverInterruptedImport(File filesDir, SharedPreferences prefs) {
        File rollbackDir = new File(filesDir, ROLLBACK_DIR);
        deleteQuietly(new File(filesDir, STAGING_DIR));
        deleteQuietly(new File(filesDir, TRASH_DIR));
        if (!new File(rollbackDir, COMMIT_MARKER).exists()) {
            deleteQuietly(rollbackDir);
            return false;
        }
        Log.w(TAG, "Rolling back an import that was interrupted");
        boolean restored = true;
        File[] items = rollbackDir.listFiles();
        if (items != null) {
            for (File item : items) {
                String name = item.getName();
                if (name.equals(COMMIT_MARKER) || name.equals(PREFS_SNAPSHOT)) {
                    continue;
                }
                String liveName = name.endsWith(ABSENT_SUFFIX)
                        ? name.substring(0, name.length() - ABSENT_SUFFIX.length()) : name;
                restored &= restoreItem(filesDir, rollbackDir, liveName, File::renameTo);
            }
        }
        try {
            restored &= ConfigArchive.restorePrefs(prefs, ConfigArchive.readPrefs(new File(rollbackDir, PREFS_SNAPSHOT)));
        } catch (IOException | ClassNotFoundException e) {
            // Retrying can't help with an unreadable snapshot; the files are restored regardless.
            Log.e(TAG, "Cannot restore the settings snapshot", e);
        }
        if (restored) {
            discardRollback(rollbackDir);
        } else {
            Log.e(TAG, "Rollback incomplete; it will be retried at the next start");
        }
        return true;
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

    /**
     * Puts {@code name} back as it was before the commit touched it. Returns false if it could
     * not; the saved copy then stays in the rollback folder for a later retry.
     */
    private static boolean restoreItem(File filesDir, File rollbackDir, String name, Mover mover) {
        File live = new File(filesDir, name);
        File saved = new File(rollbackDir, name);
        File absent = new File(rollbackDir, name + ABSENT_SUFFIX);
        if (!saved.exists() && !absent.exists()) {
            return true;  // Never touched.
        }
        deleteQuietly(live);
        if (saved.exists() && !mover.move(saved, live)) {
            Log.e(TAG, "Failed to restore " + name);
            return false;
        }
        absent.delete();
        return true;
    }

    private static void writeEmpty(File file) throws IOException {
        if (!file.createNewFile() && !file.isFile()) {
            throw new IOException("Cannot create " + file);
        }
    }

    /**
     * Deletes a file or folder tree. A symbolic link is deleted itself and never followed, so a
     * link planted by an archive can't make this delete anything outside the tree.
     */
    static void deleteQuietly(File file) {
        if (isSymbolicLink(file)) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete link " + file);
            }
            return;
        }
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) {
                deleteQuietly(child);
            }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete " + file);
        }
    }

    /**
     * True if {@code file} itself is a symbolic link. API 26+ asks java.nio.file; API 23-25 lacks
     * it, so there the link is detected by comparing canonical paths, which works on Linux.
     */
    static boolean isSymbolicLink(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.nio.file.Files.isSymbolicLink(file.toPath());
        }
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null) {
                return false;
            }
            File expected = new File(parent.getCanonicalFile(), file.getName());
            return !expected.getCanonicalFile().equals(expected.getAbsoluteFile());
        } catch (IOException e) {
            return true;  // Unresolvable: treat as a link, so it is never followed.
        }
    }
}
