package com.succession;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The succession simulation.
 *
 * <p>Stages, each step gated by light and randomness so it looks organic:
 * <pre>
 *   bare dirt --(grass creeps in from a neighbour)--> grass_block
 *   grass_block --(sunlight)--> short grass / fern / flower / (rarely) a sapling
 *   sapling --(vanilla growth)--> tree
 * </pre>
 *
 * <p>Work is bounded by sampling random blocks near online players (the same
 * approach the rest of these mods use), so cost stays flat regardless of world
 * size. Saplings then grow into trees through ordinary random ticks, so the
 * forest fills in on its own.
 */
public class SuccessionEngine {

    /** Run the sampler every this many ticks. */
    private static final int INTERVAL = 60;
    /** Random blocks examined per player per run. */
    private static final int SAMPLES_PER_PLAYER = 30;
    /** Horizontal / vertical reach of the sampler around a player. */
    private static final int RADIUS = 16;
    private static final int VERTICAL = 6;
    /** Minimum light (at the block above) for grass to spread or plants to grow. */
    private static final int LIGHT_MIN = 9;
    /** 1-in-N chance an eligible grass block gets vegetated on a given sample. */
    private static final int VEGETATE_CHANCE = 5;

    private static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET
    };

    public void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return; // surface recovery only makes sense under the open sky
        }
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }

        Random random = world.random;
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos origin = player.getBlockPos();
            for (int i = 0; i < SAMPLES_PER_PLAYER; i++) {
                int dx = random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int dz = random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int dy = random.nextInt(VERTICAL * 2 + 1) - VERTICAL;
                step(world, origin.add(dx, dy, dz), random);
            }
        }
    }

    private void step(ServerWorld world, BlockPos pos, Random random) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT)) {
            spreadGrass(world, pos);
        } else if (state.isOf(Blocks.GRASS_BLOCK)) {
            vegetate(world, pos, random);
        }
    }

    /** Bare dirt greens over when lit and bordered by existing grass. */
    private void spreadGrass(ServerWorld world, BlockPos pos) {
        if (world.getBaseLightLevel(pos.up(), 0) < LIGHT_MIN) {
            return;
        }
        if (!hasGrassNeighbor(world, pos)) {
            return; // grass only creeps outward from grass — naturally biome-correct
        }
        world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
    }

    private boolean hasGrassNeighbor(ServerWorld world, BlockPos pos) {
        for (int dy = -1; dy <= 1; dy++) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (world.getBlockState(pos.offset(dir).up(dy)).isOf(Blocks.GRASS_BLOCK)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lit grass sprouts plants, and occasionally a sapling that will grow into a tree. */
    private void vegetate(ServerWorld world, BlockPos pos, Random random) {
        BlockPos up = pos.up();
        if (!world.getBlockState(up).isAir() || world.getBaseLightLevel(up, 0) < LIGHT_MIN) {
            return;
        }
        if (random.nextInt(VEGETATE_CHANCE) != 0) {
            return; // keep growth gradual and patchy
        }

        int roll = random.nextInt(100);
        BlockState plant;
        if (roll < 3) {
            BlockState sapling = saplingFor(world, pos);
            // Some biomes (e.g. badlands) are too dry for trees — sprout grass instead.
            plant = (sapling != null) ? sapling : Blocks.SHORT_GRASS.getDefaultState();
        } else if (roll < 13) {
            plant = FLOWERS[random.nextInt(FLOWERS.length)].getDefaultState();
        } else if (roll < 28) {
            plant = Blocks.FERN.getDefaultState();
        } else {
            plant = Blocks.SHORT_GRASS.getDefaultState();
        }

        if (plant.canPlaceAt(world, up)) {
            world.setBlockState(up, plant);
        }
    }

    /** Pick a sapling that suits the biome, or null if the biome shouldn't grow trees. */
    private BlockState saplingFor(ServerWorld world, BlockPos pos) {
        var biome = world.getBiome(pos);
        if (biome.isIn(BiomeTags.IS_TAIGA)) {
            return Blocks.SPRUCE_SAPLING.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Blocks.JUNGLE_SAPLING.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_SAVANNA)) {
            return Blocks.ACACIA_SAPLING.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_BADLANDS)) {
            return null;
        }
        return Blocks.OAK_SAPLING.getDefaultState();
    }
}
