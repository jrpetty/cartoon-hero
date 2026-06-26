package dev.structint.world;

import dev.structint.Config;
import dev.structint.core.PackedPos;
import dev.structint.core.StructuralEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the whole runtime: receives block-edit notifications, schedules local stability
 * checks, and drains collapses under a per-tick budget. One instance of state per server level.
 *
 * <h2>Why edits are deferred to the tick</h2>
 * Place/break events fire mid-edit (on break the block is still present). Rather than reason
 * about that, we only record <em>that</em> a position changed and re-evaluate it on the next
 * level tick, by which point the world is settled. This also naturally batches bursts of edits.
 *
 * <h2>Completeness of a single solve</h2>
 * When we re-evaluate an edit we flood the affected cluster and solve it once. The solver only
 * ever relays support <em>through supported blocks</em>, so an unsupported block can never be
 * holding anything else up. That means one solve yields the <b>complete and final</b> set of
 * blocks that lose support from this edit — collapsing them cannot destabilise any block the
 * solve judged stable. We therefore do not need to re-flood after every collapse; we simply
 * stagger the already-known doomed set across ticks for a gradual, localized failure.
 */
public final class StructuralManager {

    private static final Map<ServerLevel, LevelStructuralState> STATES = new IdentityHashMap<>();

    private static LevelStructuralState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new LevelStructuralState());
    }

    public static void forget(ServerLevel level) {
        STATES.remove(level);
    }

    // ------------------------------------------------------------------ edit notifications

    /** A block was placed. Mark it (un)managed as appropriate and schedule a check. */
    public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        // Foundations are anchors, never managed structural blocks.
        if (BlockClassifier.isFoundation(state)) {
            StructuralData.setManaged(level, pos, false);
        } else if (BlockClassifier.isFullSolid(state, level, pos)) {
            StructuralData.setManaged(level, pos, true);
        }
        // Re-checking the placed position's cluster catches an over-reaching placement that
        // must immediately fall, and is a no-op when the placement is well supported.
        state(level).enqueueChange(pos.asLong());
    }

    /** A block was (or is about to be) removed by a player. */
    public static void onBlockRemoved(ServerLevel level, BlockPos pos) {
        StructuralData.setManaged(level, pos, false);
        state(level).enqueueChange(pos.asLong());
    }

    // ------------------------------------------------------------------ per-tick processing

    public static void tick(ServerLevel level) {
        LevelStructuralState st = STATES.get(level);
        if (st == null || st.idle()) {
            return;
        }
        processChanges(level, st);
        processCollapses(level, st);
        if (st.idle()) {
            // Keep the map small on quiet levels.
            STATES.remove(level);
        }
    }

    private static void processChanges(ServerLevel level, LevelStructuralState st) {
        int budget = Config.CHANGE_BUDGET_PER_TICK.get();
        StructuralEngine engine = newEngine(level);
        while (budget-- > 0 && st.hasChanges()) {
            long origin = st.pollChange();
            Set<Long> unsupported = engine.findUnsupported(origin);
            for (long pos : unsupported) {
                st.enqueueCollapse(pos);
            }
        }
    }

    private static void processCollapses(ServerLevel level, LevelStructuralState st) {
        if (!Config.ENABLE_COLLAPSE.get()) {
            // Drop the queue; we still tracked state, we just don't drop blocks.
            while (st.hasCollapses()) {
                st.pollCollapse();
            }
            return;
        }
        int budget = Config.COLLAPSE_BUDGET_PER_TICK.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (budget-- > 0 && st.hasCollapses()) {
            long packed = st.pollCollapse();
            cursor.set(PackedPos.x(packed), PackedPos.y(packed), PackedPos.z(packed));

            // Re-verify against the current world: the block may have been removed already, or a
            // player may have propped it up since it was queued. Fresh engine = post-mutation view.
            if (!newEngine(level).findUnsupported(packed).contains(packed)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (!BlockClassifier.isFullSolid(state, level, cursor)
                    || BlockClassifier.isFoundation(state)) {
                continue;
            }

            collapse(level, cursor.immutable(), state);
        }
    }

    private static void collapse(ServerLevel level, BlockPos pos, BlockState state) {
        StructuralData.setManaged(level, pos, false);
        // Turn it into a vanilla falling block: it drops, lands, and either settles as ground
        // or breaks into an item. No custom physics, no debris — just the vanilla gravity we
        // already trust. Removal of the source block is handled by fall().
        FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
        if (entity != null) {
            // Tag it so we can re-mark the block as player-managed when it lands (see below).
            entity.setData(ModAttachments.FROM_COLLAPSE.get(), Boolean.TRUE);
        }
    }

    /**
     * Called when a collapse-spawned falling block lands and re-forms into a block. Without this,
     * the landed block would be untracked and treated as natural terrain — letting players farm
     * free anchors by deliberately collapsing structures. We re-mark it as player-managed (so it
     * stays in the system) and re-check it (it is resting on something, so it stays put).
     */
    public static void onCollapsedBlockLanded(ServerLevel level, BlockPos pos, BlockState expected) {
        if (level.getBlockState(pos) != expected) {
            return; // it broke into an item or merged elsewhere — nothing landed here
        }
        StructuralData.setManaged(level, pos, true);
        state(level).enqueueChange(pos.asLong());
    }

    private static StructuralEngine newEngine(ServerLevel level) {
        return new StructuralEngine(
                new LevelGridAccess(level),
                Config.SUPPORT_CAP.get(),
                Config.MAX_REGION_NODES.get());
    }

    private StructuralManager() {
    }
}
