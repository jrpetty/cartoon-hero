package dev.structint.world;

import dev.structint.Config;
import dev.structint.StructuralIntegrityMod;
import dev.structint.core.PackedPos;
import dev.structint.core.StructuralEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

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
        } else if (BlockClassifier.isLoadBearing(state, level, pos)) {
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
        // One engine for the whole change phase: the world is not mutated here (collapses run
        // afterwards), so its per-pass role/state cache is valid and shared across origins.
        StructuralEngine engine = newEngine(level);
        while (budget-- > 0 && st.hasChanges()) {
            long origin = st.pollChange();
            StructuralEngine.Evaluation ev = engine.evaluate(origin);
            if (ev.overBudget) {
                StructuralIntegrityMod.LOGGER.debug(
                        "structint: structure near {} exceeds maxRegionNodes ({}); treated as stable",
                        PackedPos.toString(origin), Config.MAX_REGION_NODES.get());
            }
            long readyAt = level.getGameTime() + Config.COLLAPSE_WARN_DELAY_TICKS.get();
            for (long pos : ev.unsupported) {
                if (st.enqueueCollapse(pos, readyAt)) {
                    // Newly doomed: creak now, fall after the warning window.
                    warn(level, pos);
                }
            }
            // A block the player has since propped back up: cancel its pending collapse.
            for (long pos : ev.stable) {
                st.cancelCollapse(pos);
            }
        }
    }

    private static void processCollapses(ServerLevel level, LevelStructuralState st) {
        if (!Config.ENABLE_COLLAPSE.get()) {
            st.clearCollapses(); // we still track state, we just never drop blocks
            return;
        }
        int budget = Config.COLLAPSE_BUDGET_PER_TICK.get();
        long now = level.getGameTime();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (budget > 0 && st.hasCollapses()) {
            long packed = st.peekCollapse();
            if (!st.isActiveCollapse(packed)) {
                st.dropCollapseHead(); // stale entry for a collapse that was cancelled — discard
                continue;
            }
            if (st.readyAt(packed) > now) {
                break; // head is still in its warning window; uniform delay ⇒ nothing after is ready
            }
            st.dropCollapseHead();
            cursor.set(PackedPos.x(packed), PackedPos.y(packed), PackedPos.z(packed));

            // No re-flood needed: the change-phase solve already produced the COMPLETE unsupported
            // set (removing unsupported blocks can never re-stabilise others), and any block a
            // player re-supported was cancelled in processChanges. Just confirm it is still a
            // collapsible block (not already gone, not a foundation).
            BlockState state = level.getBlockState(cursor);
            if (!BlockClassifier.isLoadBearing(state, level, cursor)
                    || BlockClassifier.isFoundation(state)) {
                continue;
            }

            collapse(level, cursor.immutable(), state);
            budget--;
        }
    }

    /**
     * The "creak" warning played the moment a block is first judged unsupported, before it falls:
     * a muffled version of the block's own break sound plus a puff of its crumbling texture, so a
     * failure is seen and heard a beat before it happens rather than blinking out of existence.
     */
    private static void warn(ServerLevel level, long packed) {
        if (!Config.COLLAPSE_WARNING_EFFECTS.get()) {
            return;
        }
        BlockPos pos = new BlockPos(PackedPos.x(packed), PackedPos.y(packed), PackedPos.z(packed));
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS,
                Math.max(0.35F, sound.getVolume() * 0.6F), sound.getPitch() * 0.7F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                6, 0.3, 0.3, 0.3, 0.0);
    }

    /**
     * Collapsing blocks hurt whatever they land on, scaled by the material's structural strength
     * (its span): a dirt clod stings, a metal girder is worse than an anvil. Damage-per-block-
     * fallen and the total cap both grow with span; stone (span 7) lands close to a vanilla anvil
     * (2.0/block, cap 40). One knob scales both; 0 disables.
     */
    private static void applyImpactDamage(FallingBlockEntity entity, BlockState state) {
        double scale = Config.COLLAPSE_IMPACT_DAMAGE_SCALE.get();
        if (scale <= 0.0) {
            return;
        }
        int span = BlockClassifier.spanOf(state);
        float perBlockFallen = (float) (scale * (0.5 + 0.25 * span));
        int maxDamage = (int) Math.round(scale * Math.min(40.0, 4.0 + 2.5 * span));
        entity.setHurtsEntities(perBlockFallen, Math.max(1, maxDamage));
    }

    private static void collapse(ServerLevel level, BlockPos pos, BlockState state) {
        StructuralData.setManaged(level, pos, false);

        if (BlockClassifier.canFallAsEntity(state, level, pos)) {
            // A single-cell, self-contained block: animate a vanilla falling block. It drops,
            // lands, and either settles as ground or breaks into an item. Removal of the source
            // block is handled by fall().
            FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
            if (entity != null) {
                // Tag it so we can re-mark the block as player-managed when it lands (see below).
                entity.setData(ModAttachments.FROM_COLLAPSE.get(), Boolean.TRUE);
                applyImpactDamage(entity, state);
            }
        } else {
            // Multi-cell blocks (doors/beds/tall plants), attachment blocks (torches, carpets,
            // rails, …) and block-entity blocks (chests, signs, …) can't fall as a single entity
            // without being voided, duplicated, or left as a corrupt half. Drop them in place
            // instead — vanilla removes the whole object and drops its items/contents once.
            level.destroyBlock(pos, true);
        }
    }

    /**
     * Called when a collapse-spawned falling block lands and re-forms into a block. Without this,
     * the landed block would be untracked and treated as natural terrain — letting players farm
     * free anchors by deliberately collapsing structures. We re-mark it as player-managed (so it
     * stays in the system) and re-check it (it is resting on something, so it stays put).
     */
    public static void onCollapsedBlockLanded(ServerLevel level, BlockPos pos, BlockState expected) {
        if (!StructuralData.isChunkLoaded(level, pos)) {
            return; // entity left via chunk-unload/dimension-change, not a real landing
        }
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
