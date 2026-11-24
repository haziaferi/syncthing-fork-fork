package com.nutomic.syncthingandroid.backup;

/**
 * Source of the current time, so tests can fix it.
 */
public interface Clock {
    long nowMillis();

    Clock SYSTEM = System::currentTimeMillis;
}
