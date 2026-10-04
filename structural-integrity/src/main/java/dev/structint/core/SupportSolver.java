package dev.structint.core;

import java.util.PrimitiveIterator;
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
 * non-increasing in the source reach, the first time a node is popped its reach is final. The
 * queue is a bucket per reach value and positions live in primitive tables, so a solve does no
 * per-node allocation.
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
        LongHashSet cells = asLongSet(region);
        LongIntHashMap reach = reaches(cells);
        LongHashSet stable = new LongHashSet(cells.size());
        for (PrimitiveIterator.OfLong it = cells.longIterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (reach.get(pos, UNREACHED) >= 0) {
                stable.add(pos);
            }
        }
        return stable;
    }

    /** Sentinel for "no load path reached this position". Real reaches are always {@code >= 0}. */
    static final int UNREACHED = -1;

    /**
     * Runs the max-reach search over {@code region} and returns the best proven reach of every
     * position it reached. A region cell is stable exactly when it has an entry here.
     *
     * <p>Reach is a small bounded integer ({@code 0..cap}) and every edge is non-increasing, so
     * the priority queue is a bucket per reach value walked from {@code cap} down: pushes and
     * pops are O(1), nothing is allocated per entry, and the visiting order — highest reach
     * first — is exactly the Dijkstra order the correctness argument above relies on.
     */
    LongIntHashMap reaches(LongHashSet region) {
        return reaches(region, adjacentAnchors(region));
    }

    /**
     * As {@link #reaches(LongHashSet)}, with the bordering anchors already known — the flood
     * inspects every neighbour of every region cell anyway, so it collects them for free.
     */
    LongIntHashMap reaches(LongHashSet region, LongHashSet anchors) {
        LongIntHashMap best = new LongIntHashMap(region.size() + anchors.size());
        LongStack[] buckets = new LongStack[cap + 1];

        // Seed: every anchor face-adjacent to the region is an infinite-reach source.
        for (PrimitiveIterator.OfLong it = anchors.longIterator(); it.hasNext(); ) {
            long anchor = it.nextLong();
            best.put(anchor, cap);
            push(buckets, cap, anchor);
        }

        // Max-reach Dijkstra over the buckets, highest reach first. Edges never raise reach,
        // so once a bucket is drained nothing can be pushed back into it or above it.
        for (int reach = cap; reach >= 0; reach--) {
            LongStack bucket = buckets[reach];
            if (bucket == null) {
                continue;
            }
            while (!bucket.isEmpty()) {
                long pos = bucket.pop();
                if (best.get(pos, UNREACHED) > reach) {
                    continue; // stale entry: already finalised at a higher reach
                }

                // Vertical edge: the block resting directly on top of this one INHERITS this
                // block's remaining reach (no reset). Ground pillars carry CAP upward; a
                // cantilever tip carries 0, so stepping up off it grants no fresh span.
                long up = PackedPos.offset(pos, 0, 1, 0);
                if (grid.roleAt(up) == CellRole.STRUCTURAL) {
                    relax(up, reach, best, buckets);
                }

                // Horizontal edges: cantilever outward, paying one block of span per step.
                // candidate = min(reach, neighbour span) - 1, so span S reaches exactly S blocks.
                if (reach > 0) {
                    for (int d = 0; d < 4; d++) {
                        long h = PackedPos.offset(pos, DX[d], 0, DZ[d]);
                        if (grid.roleAt(h) == CellRole.STRUCTURAL) {
                            int candidate = Math.min(reach, grid.maxSpanAt(h)) - 1;
                            if (candidate >= 0) {
                                relax(h, candidate, best, buckets);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void relax(long pos, int candidate, LongIntHashMap best, LongStack[] buckets) {
        if (best.raise(pos, candidate)) {
            push(buckets, candidate, pos);
        }
    }

    /** The anchors face-adjacent to any cell of {@code region}: the search's support sources. */
    private LongHashSet adjacentAnchors(LongHashSet region) {
        LongHashSet anchors = new LongHashSet();
        for (PrimitiveIterator.OfLong it = region.longIterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            for (int d = 0; d < 6; d++) {
                long n = PackedPos.offset(pos, DX[d], DY[d], DZ[d]);
                if (!anchors.contains(n) && grid.roleAt(n) == CellRole.ANCHOR) {
                    anchors.add(n);
                }
            }
        }
        return anchors;
    }

    private static void push(LongStack[] buckets, int reach, long pos) {
        LongStack bucket = buckets[reach];
        if (bucket == null) {
            bucket = buckets[reach] = new LongStack(64);
        }
        bucket.push(pos);
    }

    static LongHashSet asLongSet(Set<Long> region) {
        if (region instanceof LongHashSet longs) {
            return longs;
        }
        LongHashSet copy = new LongHashSet(region.size());
        for (long pos : region) {
            copy.add(pos);
        }
        return copy;
    }

    /** Face offsets: the four horizontals first (the solver uses only those), then up, down. */
    static final int[] DX = {1, -1, 0, 0, 0, 0};
    static final int[] DY = {0, 0, 0, 0, 1, -1};
    static final int[] DZ = {0, 0, 1, -1, 0, 0};
}
