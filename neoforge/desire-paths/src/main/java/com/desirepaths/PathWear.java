package com.desirepaths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "The ground remembers you" logic.
 *
 * <pre>
 *   grass_block -> coarse_dirt -> dirt_path      (wear)
 *   dirt_path -> coarse_dirt -> grass_block       (reclaim)
 * </pre>
 *
 * <p>Only blocks the mod itself wore down are tracked, so naturally-generated
 * coarse dirt and vanilla dirt paths (e.g. villages) are never rewritten.
 * Partial step-progress is timestamped and periodically pruned so the tracking
 * maps can't grow without bound on a long-running server.
 */
public class PathWear {

    private static final int STEPS_PER_STAGE = 12;
    private static final long RECLAIM_AFTER_TICKS = 24_000L;
    private static final long RECLAIM_SWEEP_INTERVAL = 600L;
    private static final int RECLAIM_MAX_PER_SWEEP = 64;

    private static final class Progress {
        int count;
        long touched;
    }

    private static final class WorldState {
        final Map<UUID, BlockPos> lastPlayerPos = new HashMap<>();
        final Map<BlockPos, Progress> progress = new HashMap<>();
        final Map<BlockPos, Long> lastStep = new HashMap<>();
        final Set<BlockPos> modWorn = new HashSet<>();
    }

    private final Map<ResourceKey<Level>, WorldState> worlds = new HashMap<>();

    public void onWorldTick(ServerLevel level) {
        WorldState st = worlds.computeIfAbsent(level.dimension(), k -> new WorldState());
        long now = level.getGameTime();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.onGround()) {
                continue;
            }

            BlockPos below = player.blockPosition().below();
            BlockPos last = st.lastPlayerPos.get(player.getUUID());
            if (below.equals(last)) {
                continue;
            }
            st.lastPlayerPos.put(player.getUUID(), below);

            BlockState state = level.getBlockState(below);
            boolean canWear = state.is(Blocks.GRASS_BLOCK)
                    || (state.is(Blocks.COARSE_DIRT) && st.modWorn.contains(below));

            if (canWear) {
                Progress p = st.progress.computeIfAbsent(below, k -> new Progress());
                p.count++;
                p.touched = now;
                if (p.count >= STEPS_PER_STAGE) {
                    advance(level, below, state, st);
                    st.progress.remove(below);
                    st.lastStep.put(below, now);
                }
            } else if (state.is(Blocks.DIRT_PATH) && st.modWorn.contains(below)) {
                st.lastStep.put(below, now);
            } else {
                st.progress.remove(below);
            }
        }

        if (now % RECLAIM_SWEEP_INTERVAL == 0L) {
            pruneProgress(st, now);
            reclaim(level, st, now);
        }
    }

    private void advance(ServerLevel level, BlockPos pos, BlockState state, WorldState st) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
            st.modWorn.add(pos);
        } else if (state.is(Blocks.COARSE_DIRT)) {
            level.setBlockAndUpdate(pos, Blocks.DIRT_PATH.defaultBlockState());
            st.modWorn.add(pos);
        }
    }

    private void pruneProgress(WorldState st, long now) {
        Iterator<Map.Entry<BlockPos, Progress>> it = st.progress.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().touched >= RECLAIM_AFTER_TICKS) {
                it.remove();
            }
        }
    }

    private void reclaim(ServerLevel level, WorldState st, long now) {
        int mutations = 0;

        Iterator<Map.Entry<BlockPos, Long>> it = st.lastStep.entrySet().iterator();
        while (it.hasNext() && mutations < RECLAIM_MAX_PER_SWEEP) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (now - entry.getValue() < RECLAIM_AFTER_TICKS) {
                continue;
            }

            BlockPos pos = entry.getKey();
            BlockState cur = level.getBlockState(pos);

            if (cur.is(Blocks.DIRT_PATH)) {
                level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
                entry.setValue(now);
                mutations++;
            } else if (cur.is(Blocks.COARSE_DIRT)) {
                level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
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
