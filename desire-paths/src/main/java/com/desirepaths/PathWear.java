package com.desirepaths;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The core "the ground remembers you" logic.
 *
 * <p>Wear progression as a block is walked on:
 * <pre>grass_block → coarse_dirt → dirt_path</pre>
 * Reclaim (no traffic for a while) walks it back the other way:
 * <pre>dirt_path → coarse_dirt → grass_block</pre>
 *
 * <p>State is kept in memory per dimension. Partial step-progress is not saved
 * across a restart, but the actual block changes are — so paths persist; only
 * the in-flight counter resets.
 */
public class PathWear {

    /** Footsteps needed on a block before it advances to the next stage. */
    private static final int STEPS_PER_STAGE = 12;
    /** Ticks with no footstep before a block reclaims one stage (~1 in-game day). */
    private static final long RECLAIM_AFTER_TICKS = 24_000L;
    /** How often (ticks) to run the reclaim sweep. */
    private static final long RECLAIM_SWEEP_INTERVAL = 600L;
    /** Cap on reclaim mutations per sweep, to bound work. */
    private static final int RECLAIM_MAX_PER_SWEEP = 64;

    /** Per-dimension wear state. */
    private static final class WorldState {
        /** Block this player most recently counted a footstep on (de-dupes standing still). */
        final Map<UUID, BlockPos> lastPlayerPos = new HashMap<>();
        /** Accumulated footsteps toward the next stage, per block. */
        final Map<BlockPos, Integer> progress = new HashMap<>();
        /** Last game-time a worn block (coarse_dirt / dirt_path) saw a footstep. */
        final Map<BlockPos, Long> lastStep = new HashMap<>();
    }

    private final Map<RegistryKey<World>, WorldState> worlds = new HashMap<>();

    public void onWorldTick(ServerWorld world) {
        WorldState st = worlds.computeIfAbsent(world.getRegistryKey(), k -> new WorldState());

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isOnGround()) {
                continue;
            }

            BlockPos below = player.getBlockPos().down();
            BlockPos last = st.lastPlayerPos.get(player.getUuid());
            if (below.equals(last)) {
                continue; // same block as last footstep — don't count standing still
            }
            st.lastPlayerPos.put(player.getUuid(), below);

            BlockState state = world.getBlockState(below);

            if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.COARSE_DIRT)) {
                int steps = st.progress.merge(below, 1, Integer::sum);
                if (steps >= STEPS_PER_STAGE) {
                    advance(world, below, state);
                    st.progress.remove(below);
                    st.lastStep.put(below, world.getTime());
                }
            } else if (state.isOf(Blocks.DIRT_PATH)) {
                // Fully worn already — just keep it from reclaiming while in use.
                st.lastStep.put(below, world.getTime());
            } else {
                // Not a wearable surface; drop any stale partial progress.
                st.progress.remove(below);
            }
        }

        if (world.getTime() % RECLAIM_SWEEP_INTERVAL == 0L) {
            reclaim(world, st);
        }
    }

    /** Advance a block one stage toward a packed path. */
    private void advance(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isOf(Blocks.GRASS_BLOCK)) {
            world.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
        } else if (state.isOf(Blocks.COARSE_DIRT)) {
            world.setBlockState(pos, Blocks.DIRT_PATH.getDefaultState());
        }
    }

    /** Walk unused worn blocks back toward grass. */
    private void reclaim(ServerWorld world, WorldState st) {
        long now = world.getTime();
        int mutations = 0;

        Iterator<Map.Entry<BlockPos, Long>> it = st.lastStep.entrySet().iterator();
        while (it.hasNext() && mutations < RECLAIM_MAX_PER_SWEEP) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (now - entry.getValue() < RECLAIM_AFTER_TICKS) {
                continue;
            }

            BlockPos pos = entry.getKey();
            BlockState cur = world.getBlockState(pos);

            if (cur.isOf(Blocks.DIRT_PATH)) {
                world.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
                entry.setValue(now); // wait another full period before the next step back
                mutations++;
            } else if (cur.isOf(Blocks.COARSE_DIRT)) {
                world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
                st.progress.remove(pos);
                it.remove(); // fully reclaimed — stop tracking
                mutations++;
            } else {
                // Block was changed by something else; stop tracking it.
                st.progress.remove(pos);
                it.remove();
            }
        }
    }
}
