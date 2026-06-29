package com.desirepaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
 * <pre>
 *   grass_block -> coarse_dirt -> dirt_path      (wear)
 *   dirt_path -> coarse_dirt -> grass_block       (reclaim)
 * </pre>
 *
 * <p>Only blocks the mod itself wore down are tracked, so naturally-generated
 * coarse dirt and vanilla dirt paths (e.g. in villages) are never rewritten.
 * Partial step-progress is timestamped and periodically pruned so the tracking
 * maps can't grow without bound on a long-running server.
 */
public class PathWear {

    private static final int STEPS_PER_STAGE = 12;
    private static final long RECLAIM_AFTER_TICKS = 24_000L;
    private static final long RECLAIM_SWEEP_INTERVAL = 600L;
    private static final int RECLAIM_MAX_PER_SWEEP = 64;

    /** Accumulated footsteps toward the next stage, plus when last touched. */
    private static final class Progress {
        int count;
        long touched;
    }

    private static final class WorldState {
        final Map<UUID, BlockPos> lastPlayerPos = new HashMap<>();
        final Map<BlockPos, Progress> progress = new HashMap<>();
        final Map<BlockPos, Long> lastStep = new HashMap<>();
        /** Blocks this mod wore down — only these are eligible to wear further or reclaim. */
        final Set<BlockPos> modWorn = new HashSet<>();
    }

    private final Map<RegistryKey<World>, WorldState> worlds = new HashMap<>();

    public void onWorldTick(ServerWorld world) {
        WorldState st = worlds.computeIfAbsent(world.getRegistryKey(), k -> new WorldState());
        long now = world.getTime();

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isOnGround()) {
                continue;
            }

            BlockPos below = player.getBlockPos().down();
            BlockPos last = st.lastPlayerPos.get(player.getUuid());
            if (below.equals(last)) {
                continue;
            }
            st.lastPlayerPos.put(player.getUuid(), below);

            BlockState state = world.getBlockState(below);
            // Grass can always start a path; coarse_dirt/dirt_path only continue one we made.
            boolean canWear = state.isOf(Blocks.GRASS_BLOCK)
                    || (state.isOf(Blocks.COARSE_DIRT) && st.modWorn.contains(below));

            if (canWear) {
                Progress p = st.progress.computeIfAbsent(below, k -> new Progress());
                p.count++;
                p.touched = now;
                if (p.count >= STEPS_PER_STAGE) {
                    advance(world, below, state, st);
                    st.progress.remove(below);
                    st.lastStep.put(below, now);
                }
            } else if (state.isOf(Blocks.DIRT_PATH) && st.modWorn.contains(below)) {
                st.lastStep.put(below, now); // keep our own path from reclaiming while in use
            } else {
                st.progress.remove(below);
            }
        }

        if (now % RECLAIM_SWEEP_INTERVAL == 0L) {
            pruneProgress(st, now);
            reclaim(world, st, now);
        }
    }

    private void advance(ServerWorld world, BlockPos pos, BlockState state, WorldState st) {
        if (state.isOf(Blocks.GRASS_BLOCK)) {
            world.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
            st.modWorn.add(pos);
        } else if (state.isOf(Blocks.COARSE_DIRT)) {
            world.setBlockState(pos, Blocks.DIRT_PATH.getDefaultState());
            st.modWorn.add(pos);
        }
    }

    /** Drop partial-progress entries for blocks no one has stepped on in a long time. */
    private void pruneProgress(WorldState st, long now) {
        Iterator<Map.Entry<BlockPos, Progress>> it = st.progress.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().touched >= RECLAIM_AFTER_TICKS) {
                it.remove();
            }
        }
    }

    private void reclaim(ServerWorld world, WorldState st, long now) {
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
                entry.setValue(now);
                mutations++;
            } else if (cur.isOf(Blocks.COARSE_DIRT)) {
                world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
                st.progress.remove(pos);
                st.modWorn.remove(pos);
                it.remove();
                mutations++;
            } else {
                st.progress.remove(pos);
                st.modWorn.remove(pos);
                it.remove();
            }
        }
    }
}
