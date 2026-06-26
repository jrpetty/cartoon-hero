package dev.structint.core;

/**
 * Packs (x, y, z) block coordinates into a single {@code long}.
 *
 * <p>The bit layout is identical to vanilla {@code net.minecraft.core.BlockPos#asLong()}
 * (26 bits X, 12 bits Y, 26 bits Z). Mirroring the vanilla packing means the same
 * {@code long} keys can be shared freely between this dependency-free core and the
 * Minecraft integration layer without any translation.
 */
public final class PackedPos {

    private static final int X_BITS = 26;
    private static final int Z_BITS = 26;
    private static final int Y_BITS = 12;

    private static final long X_MASK = (1L << X_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;

    private static final int Y_SHIFT = 0;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + Z_BITS;

    private PackedPos() {
    }

    public static long of(int x, int y, int z) {
        return ((x & X_MASK) << X_SHIFT)
                | ((z & Z_MASK) << Z_SHIFT)
                | ((y & Y_MASK) << Y_SHIFT);
    }

    public static int x(long packed) {
        return (int) (packed << (64 - X_SHIFT - X_BITS) >> (64 - X_BITS));
    }

    public static int y(long packed) {
        return (int) (packed << (64 - Y_SHIFT - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long packed) {
        return (int) (packed << (64 - Z_SHIFT - Z_BITS) >> (64 - Z_BITS));
    }

    public static long offset(long packed, int dx, int dy, int dz) {
        return of(x(packed) + dx, y(packed) + dy, z(packed) + dz);
    }

    public static String toString(long packed) {
        return "(" + x(packed) + ", " + y(packed) + ", " + z(packed) + ")";
    }
}
