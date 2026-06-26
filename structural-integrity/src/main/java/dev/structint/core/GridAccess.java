package dev.structint.core;

/**
 * The solver's entire view of the world. Everything the structural algorithm needs to
 * know about a block lives behind these two queries, keyed by raw coordinates.
 *
 * <p>Keeping this interface tiny and Minecraft-free is the whole point: the algorithm in
 * {@link SupportSolver} can be exercised against a plain in-memory grid in unit tests,
 * while the live game supplies a {@code GridAccess} backed by a {@code ServerLevel}.
 */
public interface GridAccess {

    /** The structural role of the block at the given coordinates. Never {@code null}. */
    CellRole roleAt(int x, int y, int z);

    /**
     * The maximum unsupported horizontal span of the block at the given coordinates.
     * Only meaningful when {@link #roleAt} is {@link CellRole#STRUCTURAL}; the solver
     * does not call this for other roles.
     */
    int maxSpanAt(int x, int y, int z);

    default CellRole roleAt(long packed) {
        return roleAt(PackedPos.x(packed), PackedPos.y(packed), PackedPos.z(packed));
    }

    default int maxSpanAt(long packed) {
        return maxSpanAt(PackedPos.x(packed), PackedPos.y(packed), PackedPos.z(packed));
    }
}
