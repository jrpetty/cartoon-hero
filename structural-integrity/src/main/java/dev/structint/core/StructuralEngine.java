package dev.structint.core;

import java.util.PrimitiveIterator;

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
    /** Anchors bordering the last flooded region, gathered during the flood for the solver. */
    private LongHashSet lastFloodAnchors = new LongHashSet(0);
    /** Every structural cell the last flood reached — the partial cluster when it went over budget. */
    private LongHashSet lastFloodVisited = new LongHashSet(0);

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
        public final LongHashSet unsupported;
        public final LongHashSet stable;
        public final boolean overBudget;
        /**
         * The structural cells this evaluation covered: the whole cluster normally, or the part
         * of an oversized cluster seen before the budget tripped. Any later origin whose flood
         * would start inside these cells is guaranteed the same answer while the world is
         * unchanged, which lets callers skip re-evaluating it.
         */
        public final LongHashSet visited;

        Evaluation(LongHashSet unsupported, LongHashSet stable, boolean overBudget,
                   LongHashSet visited) {
            this.unsupported = unsupported;
            this.stable = stable;
            this.overBudget = overBudget;
            this.visited = visited;
        }
    }

    /**
     * Floods and solves the cluster around {@code origin} once, returning both the blocks that
     * should collapse and the blocks that are confirmed stable. Callers use the stable set to
     * cancel any previously-queued collapse a player has since propped back up.
     */
    public Evaluation evaluate(long origin) {
        LongHashSet region = floodRegion(origin);
        if (region.isEmpty()) {
            return new Evaluation(new LongHashSet(0), new LongHashSet(0), lastFloodOverBudget,
                    lastFloodVisited);
        }
        LongIntHashMap reach = solver.reaches(region, lastFloodAnchors);
        LongHashSet stable = new LongHashSet(region.size());
        LongHashSet unsupported = new LongHashSet();
        for (PrimitiveIterator.OfLong it = region.longIterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (reach.get(pos, SupportSolver.UNREACHED) >= 0) {
                stable.add(pos);
            } else {
                unsupported.add(pos);
            }
        }
        return new Evaluation(unsupported, stable, false, region);
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
    public LongHashSet floodRegion(long origin) {
        lastFloodOverBudget = false;
        LongHashSet region = new LongHashSet(256);
        LongHashSet anchors = new LongHashSet(64);
        lastFloodAnchors = anchors;
        lastFloodVisited = region;
        LongStack work = new LongStack(256);

        // Seed with origin if it is structural, otherwise with its structural neighbours
        // (covers the "block was just broken, check what it was holding up" case).
        if (grid.roleAt(origin) == CellRole.STRUCTURAL) {
            region.add(origin);
            work.push(origin);
        } else {
            for (int d = 0; d < 6; d++) {
                long n = PackedPos.offset(origin, SupportSolver.DX[d], SupportSolver.DY[d], SupportSolver.DZ[d]);
                if (grid.roleAt(n) == CellRole.STRUCTURAL && region.add(n)) {
                    work.push(n);
                }
            }
        }

        // Visiting order does not matter: the result is the whole connected cluster, and the
        // budget trips exactly when that cluster is larger than maxRegionNodes.
        while (!work.isEmpty()) {
            long pos = work.pop();
            for (int d = 0; d < 6; d++) {
                long n = PackedPos.offset(pos, SupportSolver.DX[d], SupportSolver.DY[d], SupportSolver.DZ[d]);
                if (region.contains(n)) {
                    continue;
                }
                CellRole role = grid.roleAt(n);
                if (role != CellRole.STRUCTURAL) {
                    if (role == CellRole.ANCHOR) {
                        anchors.add(n); // a support source for the solve, found for free
                    }
                    continue;
                }
                if (region.size() >= maxRegionNodes) {
                    // Budget hit: this structure is larger than we will analyse in one
                    // pass. Bail out and report "nothing unstable" rather than risk a
                    // false mass-collapse from an artificially truncated region. The
                    // caller treats an over-budget flood as a no-op (fail-safe) and can
                    // surface it via lastFloodOverBudget().
                    lastFloodOverBudget = true;
                    return new LongHashSet(0);
                }
                region.add(n);
                work.push(n);
            }
        }
        return region;
    }

    /**
     * Evaluates the cluster around {@code origin} and returns the structural blocks that are
     * now unsupported and should collapse. The result is always a subset of a single local
     * cluster &mdash; never a world-wide sweep.
     */
    public LongHashSet findUnsupported(long origin) {
        return evaluate(origin).unsupported;
    }

    /** Whether a single structural block currently has a valid load path. */
    public boolean isSupported(long pos) {
        if (grid.roleAt(pos) != CellRole.STRUCTURAL) {
            return true; // anchors and empty cells are never "unsupported"
        }
        LongHashSet region = floodRegion(pos);
        if (region.isEmpty()) {
            return true; // over-budget structures are treated as stable
        }
        return solver.reaches(region, lastFloodAnchors).get(pos, SupportSolver.UNREACHED) >= 0;
    }
}
