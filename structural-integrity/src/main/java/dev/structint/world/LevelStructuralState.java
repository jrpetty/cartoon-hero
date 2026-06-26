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

    long pollCollapse() {
        long pos = collapseQueue.dequeueLong();
        collapseSet.remove(pos);
        return pos;
    }

    boolean hasCollapses() {
        return !collapseQueue.isEmpty();
    }

    boolean idle() {
        return pendingChanges.isEmpty() && collapseQueue.isEmpty();
    }
}
