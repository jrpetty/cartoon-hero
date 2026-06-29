package com.succession;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Succession simulation:
 * <pre>
 *   bare dirt --(grass spreads from a neighbour)--> grass_block
 *   grass_block --(sunlight)--> short grass / fern / flower / (rarely) sapling
 *   sapling --(vanilla growth)--> tree
 * </pre>
 * Work is bounded by sampling random blocks near online players.
 */
public class SuccessionEngine {

    private static final int INTERVAL = 60;
    private static final int SAMPLES_PER_PLAYER = 30;
    private static final int RADIUS = 16;
    private static final int VERTICAL = 6;
    private static final int LIGHT_MIN = 9;
    private static final int VEGETATE_CHANCE = 5;

    private static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET
    };

    public void onWorldTick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }

        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            for (int i = 0; i < SAMPLES_PER_PLAYER; i++) {
                int dx = random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int dz = random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int dy = random.nextInt(VERTICAL * 2 + 1) - VERTICAL;
                step(level, origin.offset(dx, dy, dz), random);
            }
        }
    }

    private void step(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
            spreadGrass(level, pos);
        } else if (state.is(Blocks.GRASS_BLOCK)) {
            vegetate(level, pos, random);
        }
    }

    private void spreadGrass(ServerLevel level, BlockPos pos) {
        if (level.getRawBrightness(pos.above(), 0) < LIGHT_MIN) {
            return;
        }
        if (!hasGrassNeighbor(level, pos)) {
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private boolean hasGrassNeighbor(ServerLevel level, BlockPos pos) {
        for (int dy = -1; dy <= 1; dy++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(dir).above(dy)).is(Blocks.GRASS_BLOCK)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void vegetate(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos up = pos.above();
        if (!level.getBlockState(up).isAir() || level.getRawBrightness(up, 0) < LIGHT_MIN) {
            return;
        }
        if (random.nextInt(VEGETATE_CHANCE) != 0) {
            return;
        }

        int roll = random.nextInt(100);
        BlockState plant;
        if (roll < 3) {
            plant = Blocks.OAK_SAPLING.defaultBlockState();
        } else if (roll < 13) {
            plant = FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState();
        } else if (roll < 28) {
            plant = Blocks.FERN.defaultBlockState();
        } else {
            plant = Blocks.SHORT_GRASS.defaultBlockState();
        }

        if (plant.canSurvive(level, up)) {
            level.setBlockAndUpdate(up, plant);
        }
    }
}
