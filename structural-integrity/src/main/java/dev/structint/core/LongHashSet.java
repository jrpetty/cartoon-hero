package dev.structint.core;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * An insert-only open-addressing set of primitive {@code long}s — the packed positions the
 * solver works in. Every flood and solve touches thousands of positions, and a
 * {@code HashSet<Long>} would allocate a box and a node for each one; this stores them in a
 * single flat array instead.
 *
 * <p>It is still a {@code Set<Long>}, so callers that only need set semantics can treat it as
 * one, while the hot paths use the primitive {@link #add(long)}, {@link #contains(long)} and
 * {@link #longIterator()} directly. Removal is deliberately unsupported: no caller needs it,
 * and leaving it out keeps linear probing trivially correct.
 */
public final class LongHashSet extends AbstractSet<Long> {

    /** Marks an empty slot. Membership of this value itself is tracked separately. */
    private static final long FREE = Long.MIN_VALUE;

    private long[] keys;
    private int mask;
    private int resizeAt;
    private int size;
    private boolean containsFree;

    public LongHashSet() {
        this(16);
    }

    public LongHashSet(int expected) {
        allocate(tableSize(expected));
    }

    /** Smallest power-of-two table that holds {@code expected} entries at load factor 1/2. */
    static int tableSize(int expected) {
        return Integer.highestOneBit(Math.max(8, expected) * 2 - 1) << 1;
    }

    /** Spreads spatially-clustered packed positions across the table (fastutil's mixer). */
    static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (int) (h ^ (h >>> 16));
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, FREE);
        mask = capacity - 1;
        resizeAt = capacity >>> 1;
    }

    /** @return true if the set changed. */
    public boolean add(long key) {
        if (key == FREE) {
            if (containsFree) {
                return false;
            }
            containsFree = true;
            size++;
            return true;
        }
        long[] k = keys;
        int i = mix(key) & mask;
        long cur;
        while ((cur = k[i]) != FREE) {
            if (cur == key) {
                return false;
            }
            i = (i + 1) & mask;
        }
        k[i] = key;
        if (++size >= resizeAt) {
            grow();
        }
        return true;
    }

    public boolean contains(long key) {
        if (key == FREE) {
            return containsFree;
        }
        long[] k = keys;
        int i = mix(key) & mask;
        long cur;
        while ((cur = k[i]) != FREE) {
            if (cur == key) {
                return true;
            }
            i = (i + 1) & mask;
        }
        return false;
    }

    private void grow() {
        long[] old = keys;
        allocate(old.length << 1);
        long[] k = keys;
        for (long key : old) {
            if (key != FREE) {
                int i = mix(key) & mask;
                while (k[i] != FREE) {
                    i = (i + 1) & mask;
                }
                k[i] = key;
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        Arrays.fill(keys, FREE);
        size = 0;
        containsFree = false;
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof Long l && contains(l.longValue());
    }

    @Override
    public boolean add(Long key) {
        return add(key.longValue());
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        return longIterator();
    }

    /** Iterates without boxing via {@link PrimitiveIterator.OfLong#nextLong()}. */
    public PrimitiveIterator.OfLong longIterator() {
        return new PrimitiveIterator.OfLong() {
            private final long[] k = keys;
            private int slot = 0;
            private boolean freePending = containsFree;

            @Override
            public boolean hasNext() {
                if (freePending) {
                    return true;
                }
                while (slot < k.length && k[slot] == FREE) {
                    slot++;
                }
                return slot < k.length;
            }

            @Override
            public long nextLong() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                if (freePending) {
                    freePending = false;
                    return FREE;
                }
                return k[slot++];
            }
        };
    }
}
