package com.nutomic.syncthingandroid.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds a "conditions met" result for {@link #DELAY_MS} after the conditions stop being met,
 * so a brief network drop neither stops Syncthing nor pauses a folder or device (spec R09).
 * A key names what is decided: "" for the whole app, the pref prefix and ID for an object.
 */
final class StopDelay {

    static final long DELAY_MS = 10_000L;

    /** Last result returned per key. */
    private final Map<String, Boolean> lastResult = new HashMap<>();
    /** Time the conditions first went unmet during a hold, per key. */
    private final Map<String, Long> unmetSince = new HashMap<>();

    /**
     * Returns the result to act on. A true {@code met} passes through. A false one passes
     * through unless the last result for {@code key} was true; then it reads true until
     * {@link #DELAY_MS} has passed since the first unmet call.
     */
    synchronized boolean effective(String key, boolean met, long nowMs) {
        if (met) {
            lastResult.put(key, true);
            unmetSince.remove(key);
            return true;
        }
        if (!Boolean.TRUE.equals(lastResult.get(key))) {
            lastResult.put(key, false);
            return false;
        }
        Long since = unmetSince.get(key);
        if (since == null) {
            since = nowMs;
            unmetSince.put(key, since);
        }
        if (nowMs - since >= DELAY_MS) {
            lastResult.put(key, false);
            unmetSince.remove(key);
            return false;
        }
        return true;
    }

    /**
     * The earliest time after {@code nowMs} that a hold ends, or -1 when none does. A hold that
     * has already ended is left out: its key may never be checked again (a folder while
     * Syncthing is stopped), and its next {@link #effective} call returns false anyway.
     */
    synchronized long nextCheckAt(long nowMs) {
        long next = -1L;
        for (long since : unmetSince.values()) {
            long end = since + DELAY_MS;
            if (end > nowMs && (next == -1L || end < next)) {
                next = end;
            }
        }
        return next;
    }

    /**
     * Forgets every key but {@code keep}. Used when Syncthing stops: folders and devices are
     * not checked while it is stopped, so a "met" from before must not hold them later.
     */
    synchronized void clearExcept(String keep) {
        Boolean last = lastResult.get(keep);
        Long since = unmetSince.get(keep);
        clear();
        if (last != null) {
            lastResult.put(keep, last);
        }
        if (since != null) {
            unmetSince.put(keep, since);
        }
    }

    /** Forgets every key, so the next unmet result acts at once. */
    synchronized void clear() {
        lastResult.clear();
        unmetSince.clear();
    }
}
