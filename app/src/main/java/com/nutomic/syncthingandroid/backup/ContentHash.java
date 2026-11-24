package com.nutomic.syncthingandroid.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SHA-256 over a fixed canonical form of the backup's content, leaving out
 * runtime preference keys (spec R13, R22). Equal content gives an equal hash.
 */
public final class ContentHash {

    private ContentHash() {
    }

    /** @return 64 lowercase hex characters. */
    public static String of(List<File> files, Map<String, ?> prefs) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        byte[] buffer = new byte[8192];
        for (File file : files) {
            digest.update(file.getName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(longBytes(file.length()));
            try (InputStream in = new FileInputStream(file)) {
                int n;
                while ((n = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, n);
                }
            }
        }

        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.entrySet()) {
            if (!AutoBackupPrefs.isRuntimeKey(e.getKey())) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            Object value = e.getValue();
            update(digest, e.getKey());
            digest.update((byte) 0);
            update(digest, value == null ? "null" : value.getClass().getSimpleName());
            digest.update((byte) 0);
            if (value instanceof Collection) {
                List<String> items = new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    items.add(String.valueOf(item));
                }
                Collections.sort(items);
                for (String item : items) {
                    update(digest, item);
                    digest.update((byte) 0);
                }
            } else {
                update(digest, String.valueOf(value));
            }
            digest.update((byte) '\n');
        }

        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    private static void update(MessageDigest digest, String s) {
        digest.update(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] longBytes(long v) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (v & 0xff);
            v >>>= 8;
        }
        return out;
    }
}
