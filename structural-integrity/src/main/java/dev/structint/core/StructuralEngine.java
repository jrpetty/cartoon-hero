package dev.structint.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Ties the two halves of the local-check together: bound a region around a change, then
 * solve it. This is the only entry point the Minecraft layer needs for the "what just
 * became unstable?" question.
 *
 * <p>The flood is what keeps the whole system <em>local</em> and free of world scans. From
 * the changed block we walk outward through connected structural blocks only &mdash; anchors
 * and empty space form the wall of the region &mdash; stopping at a hard node budget. We then
 * hand exactly that cluster to {@link SupportSolver}. Nothing outside the touched structure
 * is ever examined.
 */
public final class StructuralEngine {

    private final GridAccess grid;
    private final SupportSolver solver;
    private final int maxRegionNodes;
    private boolean lastFloodOverBudget;

    public StructuralEngine(GridAccess grid, int cap, int maxRegionNodes) {
        this.grid = grid;
        this.solver = new SupportSolver(grid, cap);
        this.maxRegionNodes = maxRegionNodes;
    }

    /** Whether the most recent flood bailed because the cluster exceeded {@code maxRegionNodes}. */
    public boolean lastFloodOverBudget() {
        return lastFloodOverBudget;
    }

    /** Result of a full local evaluation: the doomed and the safe blocks of one cluster. */
    public static final class Evaluation {
        public final Set<Long> unsupported;
        public final Set<Long> stable;
        public final boolean overBudget;

        Evaluation(Set<Long> unsupported, Set<Long> stable, boolean overBudget) {
            this.unsupported = unsupported;
            this.stable = stable;
            this.overBudget = overBudget;
        }
    }

    /**
     * Floods and solves the cluster around {@code origin} once, returning both the blocks that
     * should collapse and the blocks that are confirmed stable. Callers use the stable set to
     * cancel any previously-queued collapse a player has since propped back up.
     */
    public Evaluation evaluate(long origin) {
        Set<Long> region = floodRegion(origin);
        if (region.isEmpty()) {
            return new Evaluation(Collections.emptySet(), Collections.emptySet(), lastFloodOverBudget);
        }
        Set<Long> stable = solver.solve(region);
        Set<Long> unsupported = new HashSet<>();
        for (long pos : region) {
            if (!stable.contains(pos)) {
                unsupported.add(pos);
            }
        }
        return new Evaluation(unsupported, stable, false);
    }

    /**
     * Gathers the connected cluster of structural blocks containing {@code origin}.
     *
     * @param origin a packed position; need not itself be structural (e.g. the spot a block
     *               was just removed from), in which case its structural neighbours seed the
     *               flood.
     * @return packed positions of the structural cluster, capped at {@code maxRegionNodes}.
     *         An empty set means there is nothing structural to worry about here.
     */
    public Set<Long> floodRegion(long origin) {
        lastFloodOverBudget = false;
        Set<Long> region = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();

        // Seed with origin if it is structural, otherwise with its structural neighbours
        // (covers the "block was just broken, check what it was holding up" case).
        if (grid.roleAt(origin) == CellRole.STRUCTURAL) {
            queue.add(origin);
            region.add(origin);
        } else {
            for (long n : sixNeighbours(origin)) {
                if (grid.roleAt(n) == CellRole.STRUCTURAL && region.add(n)) {
                    queue.add(n);
                }
            }
        }

        while (!queue.isEmpty()) {
            long pos = queue.poll();
            for (long n : sixNeighbours(pos)) {
                if (region.contains(n)) {
                    continue;
                }
                if (grid.roleAt(n) == CellRole.STRUCTURAL) {
                    if (region.size() >= maxRegionNodes) {
                        // Budget hit: this structure is larger than we will analyse in one
                        // pass. Bail out and report "nothing unstable" rather than risk a
                        // false mass-collapse from an artificially truncated region. The
                        // caller treats an over-budget flood as a no-op (fail-safe) and can
                        // surface it via lastFloodOverBudget().
                        lastFloodOverBudget = true;
                        return Collections.emptySet();
                    }
                    region.add(n);
                    queue.add(n);
                }
            }
        }
        return region;
    }

    /**
     * Evaluates the cluster around {@code origin} and returns the structural blocks that are
     * now unsupported and should collapse. The result is always a subset of a single local
     * cluster &mdash; never a world-wide sweep.
     */
    public Set<Long> findUnsupported(long origin) {
        Set<Long> region = floodRegion(origin);
        if (region.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> stable = solver.solve(region);
        Set<Long> unsupported = new HashSet<>();
        for (long pos : region) {
            if (!stable.contains(pos)) {
                unsupported.add(pos);
            }
        }
        return unsupported;
    }

    /** Whether a single structural block currently has a valid load path. */
    public boolean isSupported(long pos) {
        if (grid.roleAt(pos) != CellRole.STRUCTURAL) {
            return true; // anchors and empty cells are never "unsupported"
        }
        Set<Long> region = floodRegion(pos);
        if (region.isEmpty()) {
            return true; // over-budget structures are treated as stable
        }
        return solver.solve(region).contains(pos);
    }

    private static long[] sixNeighbours(long pos) {
        return new long[]{
                PackedPos.offset(pos, 1, 0, 0),
                PackedPos.offset(pos, -1, 0, 0),
                PackedPos.offset(pos, 0, 0, 1),
                PackedPos.offset(pos, 0, 0, -1),
                PackedPos.offset(pos, 0, 1, 0),
                PackedPos.offset(pos, 0, -1, 0),
        };
    }
}
