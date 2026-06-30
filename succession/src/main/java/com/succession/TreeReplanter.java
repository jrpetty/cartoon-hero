package com.succession;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * When a log is broken, drop a sapling (or two) on the soil under the trunk so
 * felled trees grow back. Self-deduplicating: once the base spot holds a
 * sapling, breaking the rest of the trunk won't stack more there — so a whole
 * tree yields one or two saplings, not one per log.
 */
public final class TreeReplanter {

    private static final Map<Block, Block> LOG_TO_SAPLING = new HashMap<>();
    static {
        LOG_TO_SAPLING.put(Blocks.OAK_LOG, Blocks.OAK_SAPLING);
        LOG_TO_SAPLING.put(Blocks.BIRCH_LOG, Blocks.BIRCH_SAPLING);
        LOG_TO_SAPLING.put(Blocks.SPRUCE_LOG, Blocks.SPRUCE_SAPLING);
        LOG_TO_SAPLING.put(Blocks.JUNGLE_LOG, Blocks.JUNGLE_SAPLING);
        LOG_TO_SAPLING.put(Blocks.ACACIA_LOG, Blocks.ACACIA_SAPLING);
        LOG_TO_SAPLING.put(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SAPLING);
        LOG_TO_SAPLING.put(Blocks.CHERRY_LOG, Blocks.CHERRY_SAPLING);
        LOG_TO_SAPLING.put(Blocks.MANGROVE_LOG, Blocks.MANGROVE_PROPAGULE);
    }

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int MAX_DOWN = 12;
    private static final int SECOND_SAPLING_CHANCE = 45; // percent

    private TreeReplanter() {}

    public static void onLogBroken(ServerWorld world, BlockPos brokenPos, BlockState brokenState) {
        Block sapling = LOG_TO_SAPLING.get(brokenState.getBlock());
        if (sapling == null) {
            return; // not a replantable log (stripped wood, nether stems, etc.)
        }

        BlockPos soil = findSoilBelow(world, brokenPos);
        if (soil == null) {
            return;
        }

        Random random = world.random;
        boolean planted = tryPlant(world, soil.up(), sapling);

        // "a sapling or two" — occasionally a second on adjacent soil.
        if (planted && random.nextInt(100) < SECOND_SAPLING_CHANCE) {
            BlockPos neighbor = soil.offset(HORIZONTAL[random.nextInt(HORIZONTAL.length)]);
            if (world.getBlockState(neighbor).isIn(BlockTags.DIRT)) {
                tryPlant(world, neighbor.up(), sapling);
            }
        }
    }

    /** Follow the trunk down (through air/logs/leaves) to the soil it stands on. */
    private static BlockPos findSoilBelow(ServerWorld world, BlockPos from) {
        BlockPos.Mutable cursor = from.mutableCopy();
        for (int i = 0; i < MAX_DOWN; i++) {
            BlockState state = world.getBlockState(cursor);
            if (state.isIn(BlockTags.DIRT)) {
                return cursor.toImmutable();
            }
            if (!(state.isAir() || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES))) {
                return null; // hit something that isn't part of the tree column
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean tryPlant(ServerWorld world, BlockPos pos, Block sapling) {
        BlockState existing = world.getBlockState(pos);
        if (!existing.isAir() && !existing.isReplaceable()) {
            return false; // already occupied (e.g. a sapling we just planted)
        }
        BlockState saplingState = sapling.getDefaultState();
        if (!saplingState.canPlaceAt(world, pos)) {
            return false;
        }
        world.setBlockState(pos, saplingState);
        return true;
    }
}
