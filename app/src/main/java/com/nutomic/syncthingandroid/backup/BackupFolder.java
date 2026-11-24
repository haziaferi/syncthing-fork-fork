package com.nutomic.syncthingandroid.backup;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names, order and retention of automatic backups in the backup folder
 * (spec R11, R12). Only files this feature names are ever listed or deleted.
 */
public final class BackupFolder {

    public static final String PREFIX = "config-auto-";
    public static final String SUFFIX = ".zip";
    public static final String TMP_SUFFIX = ".tmp";
    public static final int KEEP = 5;
    public static final long TMP_MAX_AGE_MS = 24 * 60 * 60 * 1000L;

    private static final String STAMP_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
    private static final Pattern BACKUP_NAME =
            Pattern.compile("config-auto-(\\d{8}T\\d{6}Z)(?:-([2-9]|[1-9]\\d+))?\\.zip");

    private BackupFolder() {
    }

    private static SimpleDateFormat stampFormat() {
        SimpleDateFormat format = new SimpleDateFormat(STAMP_FORMAT, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format;
    }

    /** The UTC time stamp used in backup names, e.g. {@code 20251123T121320Z}. */
    public static String stamp(long utcMillis) {
        return stampFormat().format(new Date(utcMillis));
    }

    /**
     * The first free name for a backup made at {@code utcMillis}: neither the
     * zip nor its {@code .tmp} exists.
     */
    public static File nextFile(File folder, long utcMillis) {
        String base = PREFIX + stamp(utcMillis);
        for (int counter = 1; ; counter++) {
            String name = counter == 1 ? base + SUFFIX : base + "-" + counter + SUFFIX;
            File file = new File(folder, name);
            if (!file.exists() && !new File(folder, name + TMP_SUFFIX).exists()) {
                return file;
            }
        }
    }

    private static final class Entry {
        final File file;
        final long time;
        final long counter;

        Entry(File file, long time, long counter) {
            this.file = file;
            this.time = time;
            this.counter = counter;
        }
    }

    /**
     * The time a new backup is named after: {@code nowMillis}, or the newest existing stamp if
     * the wall clock reads earlier, so a new backup always orders newest even after the clock
     * was set back ({@link #nextFile} then adds a counter).
     */
    public static long nameTime(File folder, long nowMillis) {
        List<Entry> entries = entries(folder);
        return entries.isEmpty() ? nowMillis : Math.max(nowMillis, entries.get(0).time);
    }

    /** The automatic backups in {@code folder}, newest first. */
    public static List<File> list(File folder) {
        List<Entry> entries = entries(folder);
        List<File> result = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            result.add(e.file);
        }
        return result;
    }

    private static List<Entry> entries(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        SimpleDateFormat format = stampFormat();
        List<Entry> entries = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            Matcher m = BACKUP_NAME.matcher(file.getName());
            if (!m.matches()) {
                continue;
            }
            long time;
            long counter;
            try {
                time = format.parse(m.group(1)).getTime();
                counter = m.group(2) == null ? 1 : Long.parseLong(m.group(2));
            } catch (ParseException | NumberFormatException e) {
                continue;
            }
            entries.add(new Entry(file, time, counter));
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.time != b.time) {
                    return a.time > b.time ? -1 : 1;
                }
                return a.counter > b.counter ? -1 : (a.counter == b.counter ? 0 : 1);
            }
        });
        return entries;
    }

    /**
     * Deletes backups beyond the newest {@link #KEEP}, and this feature's
     * {@code .tmp} files older than {@link #TMP_MAX_AGE_MS}.
     *
     * @return false if any deletion failed.
     */
    public static boolean prune(File folder, long nowMillis) {
        return prune(folder, nowMillis, null);
    }

    /** As {@link #prune(File, long)}, but never deletes {@code keep}, the backup just written. */
    public static boolean prune(File folder, long nowMillis, File keep) {
        boolean ok = true;
        List<File> backups = list(folder);
        for (int i = KEEP; i < backups.size(); i++) {
            if (backups.get(i).equals(keep)) {
                continue;
            }
            if (!backups.get(i).delete()) {
                ok = false;
            }
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.startsWith(PREFIX) && name.endsWith(SUFFIX + TMP_SUFFIX)
                        && nowMillis - file.lastModified() > TMP_MAX_AGE_MS) {
                    if (!file.delete()) {
                        ok = false;
                    }
                }
            }
        }
        return ok;
    }
}
