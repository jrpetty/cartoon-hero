package dev.structint.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The structural integrity algorithm.
 *
 * <h2>The model</h2>
 * Every structural block carries a single number: its <b>remaining reach</b> &mdash; how
 * many more blocks of horizontal cantilever it can still support beyond itself. A block is
 * <b>stable</b> if and only if some valid load path gives it a reach of {@code >= 0}.
 *
 * <p>Support flows out from {@linkplain CellRole#ANCHOR anchors} (natural terrain and
 * foundations, which have effectively infinite reach) through structural blocks along two
 * kinds of edge:
 *
 * <ul>
 *   <li><b>Vertical (resting on a support).</b> A structural block sitting directly on top of
 *       another block <em>inherits that block's remaining reach</em> — it does not reset. A
 *       pillar rooted on the ground carries the anchor's effectively-infinite reach (CAP)
 *       straight up, so pillars rise to any height and transfer load to the ground. But the tip
 *       of a maxed-out cantilever has reach 0, so stepping up one block off it inherits 0 and
 *       buys you nothing — closing the "staircase" exploit where each step-up refreshed the
 *       full horizontal budget. You only get a fresh full span by standing on real vertical
 *       support down to the ground.</li>
 *   <li><b>Horizontal (cantilevering outward).</b> Stepping sideways from a supported block
 *       with reach {@code r} into a structural neighbour costs one block of span. The
 *       neighbour's reach becomes {@code min(r, neighbour.maxSpan) - 1}: it can never exceed
 *       what its own material allows, and every horizontal step burns one unit. When reach
 *       would drop below zero, the cantilever has run out and the block is unsupported.
 *       Calibrated so a material with span {@code S} cantilevers exactly {@code S} blocks out
 *       from a vertical support or an anchor wall (dirt 1, wood 4, stone 7, …).</li>
 * </ul>
 *
 * <p>There is deliberately <b>no downward edge</b>: a block is not held up by the block
 * above it. That is what forces players to build pillars, buttresses and arches rather than
 * hanging everything from a ceiling.
 *
 * <h2>Why this satisfies the design</h2>
 * <ul>
 *   <li><b>Multi-path / redundancy.</b> Each block keeps the <em>maximum</em> reach over all
 *       paths that reach it (a longest-reach shortest-path search). A roof spanning two walls
 *       is supported from whichever wall is nearer; remove one wall and it still stands as
 *       long as the other is within span. Removing an intermediate block never collapses a
 *       structure that still has another valid load path. This is explicitly <em>not</em>
 *       single-path connectivity failure.</li>
 *   <li><b>Deterministic.</b> The result is the unique fixed point of a monotone relaxation,
 *       so it does not depend on iteration order, tie-breaking, or which block was touched.</li>
 *   <li><b>Local.</b> The solver only ever looks at the region it is handed plus the anchors
 *       bordering it; it never scans the world.</li>
 * </ul>
 *
 * <p>Implementation is a max-reach Dijkstra: anchors seed the priority queue at {@code cap},
 * and we always finalise the highest-reach frontier node first. Because both edge types are
 * non-increasing in the source reach, the first time a node is popped its reach is final.
 */
public final class SupportSolver {

    /** Default "infinite" reach assigned to anchors. Large enough to dwarf any real span. */
    public static final int DEFAULT_CAP = 64;

    private final GridAccess grid;
    private final int cap;

    public SupportSolver(GridAccess grid) {
        this(grid, DEFAULT_CAP);
    }

    public SupportSolver(GridAccess grid, int cap) {
        this.grid = grid;
        this.cap = cap;
    }

    /**
     * Computes which of the given structural blocks are supported.
     *
     * @param region packed positions of the structural blocks to evaluate. This should be a
     *               connected cluster (the caller floods it out from the changed block); any
     *               anchors bordering the cluster are discovered automatically and used as
     *               support sources.
     * @return the subset of {@code region} that is structurally supported. Anything in
     *         {@code region} but absent from the result is unsupported and should collapse.
     */
    public Set<Long> solve(Set<Long> region) {
        // best[p] = highest proven remaining reach at p. Absent => not yet reached.
        Map<Long, Integer> best = new HashMap<>(region.size() * 2);
        PriorityQueue<long[]> frontier =
                new PriorityQueue<>((a, b) -> Integer.compare((int) b[1], (int) a[1]));

        // Seed: every anchor face-adjacent to a region cell is an infinite-reach source.
        for (long pos : region) {
            for (long n : neighbours(pos)) {
                if (best.containsKey(n)) {
                    continue;
                }
                if (grid.roleAt(n) == CellRole.ANCHOR) {
                    best.put(n, cap);
                    frontier.add(new long[]{n, cap});
                }
            }
        }

        // Max-reach Dijkstra.
        while (!frontier.isEmpty()) {
            long[] top = frontier.poll();
            long pos = top[0];
            int reach = (int) top[1];

            Integer known = best.get(pos);
            if (known == null || reach < known) {
                continue; // stale queue entry, already finalised at a higher reach
            }

            // Vertical edge: the block resting directly on top of this one INHERITS this
            // block's remaining reach (no reset). Ground pillars carry CAP upward; a cantilever
            // tip carries 0, so stepping up off it grants no fresh span.
            long up = PackedPos.offset(pos, 0, 1, 0);
            if (grid.roleAt(up) == CellRole.STRUCTURAL) {
                relax(up, reach, best, frontier);
            }

            // Horizontal edges: cantilever outward, paying one block of span per step.
            // candidate = min(reach, neighbour span) - 1, so span S reaches exactly S blocks.
            if (reach > 0) {
                for (int[] d : HORIZONTAL) {
                    long h = PackedPos.offset(pos, d[0], 0, d[1]);
                    if (grid.roleAt(h) == CellRole.STRUCTURAL) {
                        int candidate = Math.min(reach, grid.maxSpanAt(h)) - 1;
                        if (candidate >= 0) {
                            relax(h, candidate, best, frontier);
                        }
                    }
                }
            }
        }

        // Anything in the region that the support flood reached is stable.
        Set<Long> stable = new HashSet<>();
        for (long pos : region) {
            Integer r = best.get(pos);
            if (r != null && r >= 0) {
                stable.add(pos);
            }
        }
        return stable;
    }

    private void relax(long pos, int candidate, Map<Long, Integer> best, PriorityQueue<long[]> frontier) {
        Integer current = best.get(pos);
        if (current == null || candidate > current) {
            best.put(pos, candidate);
            frontier.add(new long[]{pos, candidate});
        }
    }

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static long[] neighbours(long pos) {
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
