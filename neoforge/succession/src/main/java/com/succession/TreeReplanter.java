package com.succession;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

    public static void onLogBroken(ServerLevel level, BlockPos brokenPos, BlockState brokenState) {
        Block sapling = LOG_TO_SAPLING.get(brokenState.getBlock());
        if (sapling == null) {
            return; // not a replantable log (stripped wood, nether stems, etc.)
        }

        BlockPos soil = findSoilBelow(level, brokenPos);
        if (soil == null) {
            return;
        }

        RandomSource random = level.random;
        boolean planted = tryPlant(level, soil.above(), sapling);

        if (planted && random.nextInt(100) < SECOND_SAPLING_CHANCE) {
            BlockPos neighbor = soil.relative(HORIZONTAL[random.nextInt(HORIZONTAL.length)]);
            if (level.getBlockState(neighbor).is(BlockTags.DIRT)) {
                tryPlant(level, neighbor.above(), sapling);
            }
        }
    }

    /** Follow the trunk down (through air/logs/leaves) to the soil it stands on. */
    private static BlockPos findSoilBelow(ServerLevel level, BlockPos from) {
        BlockPos.MutableBlockPos cursor = from.mutable();
        for (int i = 0; i < MAX_DOWN; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(BlockTags.DIRT)) {
                return cursor.immutable();
            }
            if (!(state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean tryPlant(ServerLevel level, BlockPos pos, Block sapling) {
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        BlockState saplingState = sapling.defaultBlockState();
        if (!saplingState.canSurvive(level, pos)) {
            return false;
        }
        level.setBlockAndUpdate(pos, saplingState);
        return true;
    }
}
