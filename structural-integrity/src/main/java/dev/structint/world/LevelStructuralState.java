package dev.structint.world;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Per-level mutable work queues. Two stages, both drained under a per-tick budget so a single
 * giant edit can never stall the server:
 *
 * <ol>
 *   <li>{@code pendingChanges} — origins of block edits that still need a stability check;</li>
 *   <li>{@code collapseQueue} — positions already proven unsupported, waiting to fall.</li>
 * </ol>
 *
 * Each queue is paired with a membership set so the same position is never enqueued twice.
 */
final class LevelStructuralState {

    final LongArrayFIFOQueue pendingChanges = new LongArrayFIFOQueue();
    final LongOpenHashSet pendingSet = new LongOpenHashSet();

    final LongArrayFIFOQueue collapseQueue = new LongArrayFIFOQueue();
    final LongOpenHashSet collapseSet = new LongOpenHashSet();

    void enqueueChange(long pos) {
        if (pendingSet.add(pos)) {
            pendingChanges.enqueue(pos);
        }
    }

    long pollChange() {
        long pos = pendingChanges.dequeueLong();
        pendingSet.remove(pos);
        return pos;
    }

    boolean hasChanges() {
        return !pendingChanges.isEmpty();
    }

    void enqueueCollapse(long pos) {
        if (collapseSet.add(pos)) {
            collapseQueue.enqueue(pos);
        }
    }

    /**
     * Cancel a pending collapse a block has regained support for. The position is dropped from
     * the active set but its (now-stale) FIFO entry is skipped lazily on poll, so this is O(1).
     */
    void cancelCollapse(long pos) {
        collapseSet.remove(pos);
    }

    /** Dequeue the next FIFO entry. May be a stale (cancelled) entry — check {@link #claimCollapse}. */
    long dequeueCollapse() {
        return collapseQueue.dequeueLong();
    }

    /** @return true if the position was still an active collapse (and claims it); false if cancelled. */
    boolean claimCollapse(long pos) {
        return collapseSet.remove(pos);
    }

    void clearCollapses() {
        collapseQueue.clear();
        collapseSet.clear();
    }

    boolean hasCollapses() {
        return !collapseQueue.isEmpty();
    }

    boolean idle() {
        return pendingChanges.isEmpty() && collapseQueue.isEmpty();
    }
}
