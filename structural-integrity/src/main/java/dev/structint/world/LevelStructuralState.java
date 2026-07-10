package dev.structint.world;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
 * Each queue is paired with a membership set so the same position is never enqueued twice. A
 * doomed block also carries a {@code readyAt} game-time: the tick it may actually fall, giving a
 * short "creak" warning window between the first sign of failure and the collapse itself. Because
 * that delay is uniform, FIFO insertion order equals ready order, so the head of the queue is
 * always the next block due to fall.
 */
final class LevelStructuralState {

    final LongArrayFIFOQueue pendingChanges = new LongArrayFIFOQueue();
    final LongOpenHashSet pendingSet = new LongOpenHashSet();

    final LongArrayFIFOQueue collapseQueue = new LongArrayFIFOQueue();
    final LongOpenHashSet collapseSet = new LongOpenHashSet();
    final Long2LongOpenHashMap collapseReadyAt = new Long2LongOpenHashMap();

    LevelStructuralState() {
        collapseReadyAt.defaultReturnValue(0L);
    }

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

    /** @return true if the position was newly queued (so the caller should play the warning). */
    boolean enqueueCollapse(long pos, long readyAt) {
        if (collapseSet.add(pos)) {
            collapseQueue.enqueue(pos);
            collapseReadyAt.put(pos, readyAt);
            return true;
        }
        return false;
    }

    /**
     * Cancel a pending collapse a block has regained support for. The position is dropped from
     * the active set but its (now-stale) FIFO entry is skipped lazily on poll, so this is O(1).
     */
    void cancelCollapse(long pos) {
        collapseSet.remove(pos);
    }

    boolean hasCollapses() {
        return !collapseQueue.isEmpty();
    }

    /** Peek the head of the FIFO without removing it. */
    long peekCollapse() {
        return collapseQueue.firstLong();
    }

    boolean isActiveCollapse(long pos) {
        return collapseSet.contains(pos);
    }

    long readyAt(long pos) {
        return collapseReadyAt.get(pos);
    }

    /** Remove and forget the head entry (whether it was collapsed or was a stale/cancelled entry). */
    void dropCollapseHead() {
        long pos = collapseQueue.dequeueLong();
        collapseSet.remove(pos);
        collapseReadyAt.remove(pos);
    }

    void clearCollapses() {
        collapseQueue.clear();
        collapseSet.clear();
        collapseReadyAt.clear();
    }

    boolean idle() {
        return pendingChanges.isEmpty() && collapseQueue.isEmpty();
    }
}
