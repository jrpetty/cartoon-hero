package dev.structint.core;

import java.util.Arrays;

/** A growable LIFO of primitive {@code long}s: the flood's work list and the solver's buckets. */
final class LongStack {

    private long[] data;
    private int size;

    LongStack(int initialCapacity) {
        data = new long[Math.max(4, initialCapacity)];
    }

    void push(long value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, size << 1);
        }
        data[size++] = value;
    }

    long pop() {
        return data[--size];
    }

    boolean isEmpty() {
        return size == 0;
    }
}
