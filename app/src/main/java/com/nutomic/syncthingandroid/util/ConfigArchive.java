package com.nutomic.syncthingandroid.util;

import android.content.SharedPreferences;
import android.util.Log;

import com.nutomic.syncthingandroid.backup.AutoBackupPrefs;
import com.nutomic.syncthingandroid.service.Constants;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.UnzipParameters;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the config backup zip and the serialised SharedPreferences inside it.
 * Holds no Context, so it can be tested on the JVM.
 */
public final class ConfigArchive {

    private static final String TAG = "ConfigArchive";

    /** Prefs that are no longer used, left over from previous versions of the app. */
    private static final Set<String> DEPRECATED_PREFS = new HashSet<>(Arrays.asList(
            "first_start",
            "advanced_folder_picker",
            "backup_folder_name",
            "bind_network",
            "log_to_file",
            "notification_type",
            "notify_crashes",
            "suggest_new_folder_root",
            "use_legacy_hashing",
            "pref_current_language",
            "restartOnWakeup",
            "wakelock_while_binary_running",
            "use_root",
            "important_news_shown_version"
    ));

    /** Cached device-specific state that must not be restored from a backup. */
    private static final Set<String> CACHE_PREFS = new HashSet<>(Arrays.asList(
            Constants.PREF_APP_START_COUNTER,
            Constants.PREF_BTNSTATE_FORCE_START_STOP,
            Constants.PREF_DEBUG_FACILITIES_AVAILABLE,
            Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID,
            Constants.PREF_LAST_BINARY_VERSION,
            Constants.PREF_LOCAL_DEVICE_ID,
            Constants.PREF_LAST_RUN_TIME
    ));

    static {
        // The automatic backup's status belongs to this device (spec R16).
        CACHE_PREFS.addAll(AutoBackupPrefs.STATUS_KEYS);
    }

    public enum Check {
        OK,
        MISSING_ZIP,
        NOT_ENCRYPTED_BUT_PASSWORD_SET,
        MISSING_REQUIRED_ENTRY,
        /** Wrong password or corrupt zip. */
        CANNOT_OPEN
    }

    private ConfigArchive() {
    }

    private static ZipFile open(File zip, String password) {
        return password.isEmpty() ? new ZipFile(zip) : new ZipFile(zip, password.toCharArray());
    }

    /**
     * Writes a fresh zip (any existing file is deleted first). Paths that don't exist are
     * skipped; directories are added recursively. A non-empty password encrypts with AES-256.
     */
    public static void writeZip(File targetZip, List<File> includePaths, String password) throws IOException {
        if (targetZip.exists()) {
            targetZip.delete();
        }
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(CompressionLevel.NORMAL);
        if (password.isEmpty()) {
            parameters.setEncryptFiles(false);
        } else {
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(EncryptionMethod.AES);
            parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }
        ZipFile zipFile = open(targetZip, password);
        for (File includePath : includePaths) {
            if (!includePath.exists()) {
                continue;
            }
            if (includePath.isFile()) {
                zipFile.addFile(includePath, parameters);
            } else if (includePath.isDirectory()) {
                zipFile.addFolder(includePath, parameters);
            }
        }
    }

    /**
     * Checks that the zip exists, matches the password setting, holds every required entry,
     * and can be decrypted (by a trial extraction of {@link Constants#PUBLIC_KEY_FILE} into
     * {@code scratchDir}, which is deleted again).
     */
    public static Check check(File zip, String password, List<String> requiredEntries, File scratchDir) {
        if (!zip.exists()) {
            Log.e(TAG, "ZIP file is missing at '" + zip.getAbsolutePath() + "'");
            return Check.MISSING_ZIP;
        }
        try {
            ZipFile zipFile = open(zip, password);
            if (!password.isEmpty() && !zipFile.isEncrypted()) {
                Log.e(TAG, "ZIP file is not encrypted, but a password was specified.");
                return Check.NOT_ENCRYPTED_BUT_PASSWORD_SET;
            }
            // Log every missing entry, so one attempt shows all that needs fixing.
            boolean missing = false;
            for (String entry : requiredEntries) {
                if (zipFile.getFileHeader(entry) == null) {
                    Log.e(TAG, "Required file not found inside zip [" + entry + "]");
                    missing = true;
                }
            }
            if (missing) {
                return Check.MISSING_REQUIRED_ENTRY;
            }
            String scratch = scratchDir.getAbsolutePath();
            zipFile.extractFile(Constants.PUBLIC_KEY_FILE, scratch, unzipParameters());
            new File(scratchDir, Constants.PUBLIC_KEY_FILE).delete();
        } catch (ZipException e) {
            Log.e(TAG, "Failed to open zip, " + e.getMessage());
            return Check.CANNOT_OPEN;
        }
        return Check.OK;
    }

    /**
     * Extracts every entry. Symbolic link entries are never created as links, so an archive
     * can't plant a link pointing outside {@code destDir}.
     */
    public static void extractAll(File zip, String password, File destDir) throws IOException {
        open(zip, password).extractAll(destDir.getAbsolutePath(), unzipParameters());
    }

    /**
     * Settings for every extraction from a backup: symbolic link entries are skipped. zip4j
     * creates them by default, which would let an archive plant a link, and on API 23-25
     * (no java.nio.file) crash the app.
     */
    static UnzipParameters unzipParameters() {
        UnzipParameters unzipParameters = new UnzipParameters();
        unzipParameters.setExtractSymbolicLinks(false);
        return unzipParameters;
    }

    /** Serialises prefs the way every earlier release did, so old backups stay readable. */
    public static void writePrefs(Map<String, ?> prefs, File file) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(prefs);
            objectOutputStream.flush();
            fileOutputStream.flush();
        }
    }

    /**
     * The only classes a prefs backup may contain: what {@code SharedPreferences.getAll()}
     * returns. {@code Number} is needed because deserialising a boxed number also resolves its
     * superclass. Strings are written without a class descriptor, so they need no entry.
     */
    static final Set<String> PREFS_ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
            "java.util.HashMap",
            "java.util.HashSet",
            "java.lang.Boolean",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Number"
    ));

    /** Refuses to load any class outside {@link #PREFS_ALLOWED_CLASSES}. */
    private static final class AllowListObjectInputStream extends ObjectInputStream {
        AllowListObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (!PREFS_ALLOWED_CLASSES.contains(desc.getName())) {
                throw new InvalidClassException(desc.getName(), "not allowed in prefs backup");
            }
            return super.resolveClass(desc);
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
            throw new InvalidClassException("proxy", "not allowed in prefs backup");
        }
    }

    /**
     * Reads prefs written by {@link #writePrefs}. Only the classes in
     * {@link #PREFS_ALLOWED_CLASSES} are accepted, and the top-level object must be a map;
     * anything else throws {@link InvalidClassException} before any pref is changed.
     */
    public static Map<String, Object> readPrefs(File file) throws IOException, ClassNotFoundException {
        try (FileInputStream fileInputStream = new FileInputStream(file);
             ObjectInputStream objectInputStream = new AllowListObjectInputStream(fileInputStream)) {
            Object object = objectInputStream.readObject();
            if (!(object instanceof Map)) {
                throw new InvalidClassException(object == null ? "null" : object.getClass().getName(),
                        "prefs backup must hold a map");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) object).entrySet()) {
                if (!(e.getKey() instanceof String)) {
                    Log.w(TAG, "Ignoring pref with a non-string key");
                    continue;
                }
                result.put((String) e.getKey(), e.getValue());
            }
            return result;
        }
    }

    /**
     * Keeps only prefs that should be restored: drops deprecated and cache keys, and values
     * that SharedPreferences cannot store.
     */
    public static Map<String, Object> importablePrefs(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                Log.w(TAG, "Ignoring pref with a non-string key");
                continue;
            }
            String key = (String) e.getKey();
            Object value = e.getValue();
            if (DEPRECATED_PREFS.contains(key)) {
                Log.v(TAG, "Ignoring deprecated pref \"" + key + "\".");
            } else if (CACHE_PREFS.contains(key)) {
                Log.v(TAG, "Ignoring cache pref \"" + key + "\".");
            } else if (value instanceof Boolean || value instanceof String || value instanceof Integer
                    || value instanceof Float || value instanceof Long || isStringSet(value)) {
                result.put(key, value);
            } else {
                Log.w(TAG, "Ignoring pref \"" + key + "\" of unsupported type "
                        + (value == null ? "null" : value.getClass().getName()));
            }
        }
        return result;
    }

    private static boolean isStringSet(Object value) {
        if (!(value instanceof Set)) {
            return false;
        }
        for (Object o : (Set<?>) value) {
            // Null items are kept, as earlier releases did.
            if (o != null && !(o instanceof String)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Replaces all prefs with {@code importable}, then restores the current backup location
     * and password and the device's automatic backup status ({@code keepStatus}), and commits.
     */
    public static boolean applyPrefs(SharedPreferences.Editor editor, Map<String, Object> importable,
                                     String keepRelPathToZip, String keepPassword,
                                     Map<String, ?> keepStatus) {
        editor.clear();
        for (Map.Entry<String, Object> e : importable.entrySet()) {
            Log.i(TAG, "Adding pref \"" + e.getKey() + "\" to commit ...");
            putTyped(editor, e.getKey(), e.getValue());
        }
        editor.putString(Constants.PREF_BACKUP_REL_PATH_TO_ZIP, keepRelPathToZip);
        editor.putString(Constants.PREF_BACKUP_PASSWORD, keepPassword);
        for (Map.Entry<String, ?> e : keepStatus.entrySet()) {
            putTyped(editor, e.getKey(), e.getValue());
        }
        return editor.commit();
    }

    /** Replaces all prefs with exactly {@code snapshot} (as returned by {@code getAll()}) and commits. */
    public static boolean restorePrefs(SharedPreferences prefs, Map<String, ?> snapshot) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (Map.Entry<String, ?> e : snapshot.entrySet()) {
            putTyped(editor, e.getKey(), e.getValue());
        }
        return editor.commit();
    }

    private static void putTyped(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Set) {
            Set<String> strings = new HashSet<>();
            for (Object o : (Set<?>) value) {
                strings.add((String) o);
            }
            editor.putStringSet(key, strings);
        }
    }
}
