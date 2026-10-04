package dev.structint.core;

import java.util.Arrays;

/**
 * An insert/overwrite-only open-addressing map from primitive {@code long} to {@code int} —
 * the solver's "best proven reach per position" table, without a boxed key and value per entry.
 */
final class LongIntHashMap {

    private static final long FREE = Long.MIN_VALUE;

    private long[] keys;
    private int[] values;
    private int mask;
    private int resizeAt;
    private int size;
    private boolean hasFreeKey;
    private int freeKeyValue;

    LongIntHashMap(int expected) {
        allocate(LongHashSet.tableSize(expected));
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, FREE);
        values = new int[capacity];
        mask = capacity - 1;
        resizeAt = capacity >>> 1;
    }

    /** @return the value for {@code key}, or {@code missing} if it has none. */
    int get(long key, int missing) {
        if (key == FREE) {
            return hasFreeKey ? freeKeyValue : missing;
        }
        long[] k = keys;
        int i = LongHashSet.mix(key) & mask;
        long cur;
        while ((cur = k[i]) != FREE) {
            if (cur == key) {
                return values[i];
            }
            i = (i + 1) & mask;
        }
        return missing;
    }

    /**
     * Stores {@code value} if {@code key} is absent or currently maps to something lower — one
     * probe instead of a {@code get} followed by a {@code put}.
     *
     * @return true if the stored value changed
     */
    boolean raise(long key, int value) {
        if (key == FREE) {
            if (hasFreeKey && freeKeyValue >= value) {
                return false;
            }
            hasFreeKey = true;
            freeKeyValue = value;
            return true;
        }
        long[] k = keys;
        int i = LongHashSet.mix(key) & mask;
        long cur;
        while ((cur = k[i]) != FREE) {
            if (cur == key) {
                if (values[i] >= value) {
                    return false;
                }
                values[i] = value;
                return true;
            }
            i = (i + 1) & mask;
        }
        k[i] = key;
        values[i] = value;
        if (++size >= resizeAt) {
            grow();
        }
        return true;
    }

    void put(long key, int value) {
        if (key == FREE) {
            hasFreeKey = true;
            freeKeyValue = value;
            return;
        }
        long[] k = keys;
        int i = LongHashSet.mix(key) & mask;
        long cur;
        while ((cur = k[i]) != FREE) {
            if (cur == key) {
                values[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
        k[i] = key;
        values[i] = value;
        if (++size >= resizeAt) {
            grow();
        }
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        allocate(oldKeys.length << 1);
        long[] k = keys;
        for (int j = 0; j < oldKeys.length; j++) {
            long key = oldKeys[j];
            if (key != FREE) {
                int i = LongHashSet.mix(key) & mask;
                while (k[i] != FREE) {
                    i = (i + 1) & mask;
                }
                k[i] = key;
                values[i] = oldValues[j];
            }
        }
    }
}
