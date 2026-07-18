package com.jrpetty.aztecabyss.worldgen;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.Random;

/**
 * Orchestrates the whole Abyss arena the first time the dimension is loaded:
 * the bounded floor, the bedrock perimeter wall (backed by a matching world
 * border), the temple, the forest ring you see it through, ore veins, a
 * handful of loot chests, and the always-lit arrival portal.
 *
 * Fully deterministic - re-running it is a no-op once the sentinel block at
 * the temple's core exists, and every random element uses a fixed seed, so
 * every server gets the exact same arena.
 */
public final class ArenaGenerator {

    private static final long WORLDGEN_SEED = 20260718L;

    private ArenaGenerator() {
    }

    public static void generateIfNeeded(ServerLevel level) {
        BlockState sentinel = level.getBlockState(AztecAbyssConstants.TEMPLE_CENTER);
        if (sentinel.is(Blocks.GILDED_BLACKSTONE)) {
            return; // already built
        }

        carveFloorAndClearAir(level);
        buildPerimeterWall(level);
        decoratePerimeter(level);
        applyWorldBorder(level);
        TempleBuilder.build(level, AztecAbyssConstants.TEMPLE_CENTER);
        plantForest(level);
        placeOreVeins(level);
        placeLootChests(level);
        placeArrivalPortal(level);
        MonumentBuilder.build(level);
    }

    /**
     * Dresses the inner face of the bedrock wall to sell the "Upside Down"
     * boundary: creeping sculk, hanging vine tendrils, glowing shriekers, and
     * half-buried skulls staring inward. Deterministic (fixed seed).
     */
    private static void decoratePerimeter(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 7);
        int radius = AztecAbyssConstants.ARENA_RADIUS - 1;
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int top = floorY + AztecAbyssConstants.WALL_HEIGHT;

        int points = 200;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 / points) * i + rng.nextDouble() * 0.02;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);

            // Which way is the wall (outward from centre)?
            Direction outward = Math.abs(x) >= Math.abs(z)
                    ? (x >= 0 ? Direction.EAST : Direction.WEST)
                    : (z >= 0 ? Direction.SOUTH : Direction.NORTH);

            // Sculk creep at the base.
            for (int dy = 0; dy <= 2 + rng.nextInt(3); dy++) {
                BlockPos p = new BlockPos(x, floorY + dy, z);
                if (level.getBlockState(p).isAir() && rng.nextDouble() < 0.5) {
                    level.setBlock(p, Blocks.SCULK.defaultBlockState(), 2);
                }
            }
            // A glowing shrieker now and then.
            if (rng.nextInt(14) == 0) {
                level.setBlock(new BlockPos(x, floorY + 1, z), Blocks.SCULK_SHRIEKER.defaultBlockState(), 2);
            }
            // Half-buried face: a skull on the floor staring inward.
            if (rng.nextInt(9) == 0) {
                BlockState skull = switch (rng.nextInt(3)) {
                    case 0 -> Blocks.WITHER_SKELETON_SKULL.defaultBlockState();
                    case 1 -> Blocks.ZOMBIE_HEAD.defaultBlockState();
                    default -> Blocks.SKELETON_SKULL.defaultBlockState();
                };
                skull = skull.setValue(BlockStateProperties.ROTATION_16, rng.nextInt(16));
                BlockPos sp = new BlockPos(x, floorY + 1, z);
                if (level.getBlockState(sp).isAir()) {
                    level.setBlock(sp, skull, 2);
                }
            }
            // Hanging vine tendrils down the wall.
            if (rng.nextInt(3) == 0) {
                int len = 3 + rng.nextInt(10);
                int startY = top - rng.nextInt(8);
                BooleanProperty face = switch (outward) {
                    case EAST -> BlockStateProperties.EAST;
                    case WEST -> BlockStateProperties.WEST;
                    case SOUTH -> BlockStateProperties.SOUTH;
                    default -> BlockStateProperties.NORTH;
                };
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(face, true);
                for (int dy = 0; dy < len; dy++) {
                    BlockPos vp = new BlockPos(x, startY - dy, z);
                    if (startY - dy <= floorY) {
                        break;
                    }
                    if (level.getBlockState(vp).isAir()) {
                        level.setBlock(vp, vine, 2);
                    }
                }
            }
        }
    }

    private static void carveFloorAndClearAir(ServerLevel level) {
        int radius = AztecAbyssConstants.ARENA_RADIUS;
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        Random rng = new Random(WORLDGEN_SEED);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                BlockPos floorPos = new BlockPos(x, floorY, z);
                level.setBlock(floorPos, floorMaterial(rng), 2);
                level.setBlock(new BlockPos(x, floorY - 1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int y = floorY + 1; y <= floorY + AztecAbyssConstants.WALL_HEIGHT; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static BlockState floorMaterial(Random rng) {
        double roll = rng.nextDouble();
        if (roll < 0.55) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (roll < 0.8) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (roll < 0.93) {
            return Blocks.STONE.defaultBlockState();
        }
        return Blocks.ANDESITE.defaultBlockState();
    }

    private static void buildPerimeterWall(ServerLevel level) {
        int radius = AztecAbyssConstants.ARENA_RADIUS;
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int top = floorY + AztecAbyssConstants.WALL_HEIGHT;
        int thickness = 4;

        for (int x = -radius - thickness; x <= radius + thickness; x++) {
            for (int z = -radius - thickness; z <= radius + thickness; z++) {
                double dist2 = (double) x * x + (double) z * z;
                if (dist2 < (double) radius * radius || dist2 > (double) (radius + thickness) * (radius + thickness)) {
                    continue;
                }
                for (int y = floorY - 1; y <= top; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void applyWorldBorder(ServerLevel level) {
        // Belt-and-suspenders alongside the physical bedrock wall: nobody is
        // pushing, elytra-gliding, or piston-launching their way past this.
        level.getWorldBorder().setCenter(AztecAbyssConstants.TEMPLE_CENTER.getX(), AztecAbyssConstants.TEMPLE_CENTER.getZ());
        level.getWorldBorder().setSize((AztecAbyssConstants.ARENA_RADIUS - 2) * 2.0);
    }

    private static void plantForest(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 1);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int innerEdge = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 14;
        int outerEdge = AztecAbyssConstants.ARENA_RADIUS - 8;

        for (int i = 0; i < 260; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = innerEdge + rng.nextDouble() * (outerEdge - innerEdge);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);

            // Keep a clear sightline/approach directly south of the arrival portal.
            if (z > 40 && Math.abs(x) < 6) {
                continue;
            }
            plantTree(level, new BlockPos(x, floorY + 1, z), rng);
        }
    }

    private static void plantTree(ServerLevel level, BlockPos base, Random rng) {
        boolean darkOak = rng.nextBoolean();
        BlockState log = (darkOak ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
        BlockState leaves = (darkOak ? Blocks.DARK_OAK_LEAVES : Blocks.SPRUCE_LEAVES).defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true);
        int height = 5 + rng.nextInt(4);

        if (!level.getBlockState(base.below()).is(Blocks.AIR)) {
            for (int h = 0; h < height; h++) {
                level.setBlock(base.above(h), log, 2);
            }
            int canopyR = 2 + rng.nextInt(2);
            for (int dx = -canopyR; dx <= canopyR; dx++) {
                for (int dz = -canopyR; dz <= canopyR; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (dx * dx + dz * dz <= canopyR * canopyR + 1 && rng.nextDouble() < 0.85) {
                            BlockPos leafPos = base.offset(dx, height - 2 + dy, dz);
                            if (level.getBlockState(leafPos).isAir()) {
                                level.setBlock(leafPos, leaves, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void placeOreVeins(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 2);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        BlockState[] ores = {
                Blocks.DIAMOND_ORE.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState(),
                Blocks.COAL_ORE.defaultBlockState()
        };

        for (int i = 0; i < 40; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 4 + rng.nextDouble() * 60;
            int cx = (int) Math.round(Math.cos(angle) * dist);
            int cz = (int) Math.round(Math.sin(angle) * dist);
            BlockState ore = ores[rng.nextInt(ores.length)];

            int veinSize = 2 + rng.nextInt(3);
            for (int v = 0; v < veinSize; v++) {
                int ox = cx + rng.nextInt(3) - 1;
                int oz = cz + rng.nextInt(3) - 1;
                level.setBlock(new BlockPos(ox, floorY, oz), ore, 2);
            }
        }
    }

    private static void placeLootChests(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 3);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int placed = 0;
        int attempts = 0;

        while (placed < 6 && attempts < 200) {
            attempts++;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 6 + rng.nextDouble() * 70;
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            BlockPos pos = new BlockPos(x, floorY + 1, z);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity chest) {
                fillStashChest(chest, rng);
            }
            placed++;
        }
    }

    private static void fillStashChest(ChestBlockEntity chest, Random rng) {
        net.minecraft.world.item.Item[] pool = {
                net.minecraft.world.item.Items.IRON_INGOT,
                net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.EMERALD,
                net.minecraft.world.item.Items.ARROW,
                net.minecraft.world.item.Items.BREAD,
                net.minecraft.world.item.Items.TORCH,
                net.minecraft.world.item.Items.GOLDEN_APPLE,
                net.minecraft.world.item.Items.DIAMOND
        };
        int rolls = 2 + rng.nextInt(3);
        for (int i = 0; i < rolls; i++) {
            net.minecraft.world.item.Item item = pool[rng.nextInt(pool.length)];
            int count = item == net.minecraft.world.item.Items.DIAMOND || item == net.minecraft.world.item.Items.GOLDEN_APPLE
                    ? 1 + rng.nextInt(2)
                    : 1 + rng.nextInt(6);
            chest.setItem(rng.nextInt(chest.getContainerSize()), new net.minecraft.world.item.ItemStack(item, count));
        }
    }

    private static void placeArrivalPortal(ServerLevel level) {
        BlockPos arrival = AztecAbyssConstants.ABYSS_ARRIVAL_POS;
        // A 4-wide x 5-tall frame (interior 2x3) built on the X axis, facing the temple to the north.
        int floorY = arrival.getY() - 1;
        int cx = arrival.getX();
        int cz = arrival.getZ();

        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean frameEdge = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                BlockPos pos = new BlockPos(cx + dx, floorY + dy, cz);
                if (frameEdge) {
                    level.setBlock(pos, ModBlocks.ABYSSAL_OBSIDIAN.get().defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, ModBlocks.ABYSS_PORTAL.get().defaultBlockState()
                            .setValue(com.jrpetty.aztecabyss.block.AbyssPortalBlock.AXIS, Direction.Axis.X), 3);
                }
            }
        }
    }

    /** Cheap ambient mist + brazier crackle around the temple base. Called once per level tick. */
    public static void ambientTick(ServerLevel level) {
        if (level.players().isEmpty() || level.getGameTime() % 4 != 0) {
            return;
        }
        RandomSource rng = level.random;
        int hw = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH;
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int round = com.jrpetty.aztecabyss.round.RoundManager.game().getRound();

        // Mist thickens with the round.
        int puffs = 4 + round / 3;
        for (int i = 0; i < puffs; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = hw + rng.nextDouble() * 10;
            double x = Math.cos(angle) * dist;
            double z = Math.sin(angle) * dist;
            level.sendParticles(ParticleTypes.WHITE_ASH, x, floorY + 1.2, z, 1, 0.5, 0.1, 0.5, 0.0);
        }
        if (rng.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.WHITE_ASH, 0, floorY + 2, 0, 2, hw * 0.6, 0.3, hw * 0.6, 0.01);
        }
        // Bats: a small, capped population of skittish ambient mobs.
        maybeSpawnBats(level, rng);
    }

    /**
     * The temple reacts to the escalating round: the altar erupts into a taller
     * lava/ember fountain and the summit + braziers flare. Implemented with
     * particles rather than spreading lava blocks so the play floor stays safe.
     * Called once per round start.
     */
    public static void escalateTemple(ServerLevel level, int round) {
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        double ax = AztecAbyssConstants.ALTAR_POS.getX() + 0.5;
        double az = AztecAbyssConstants.ALTAR_POS.getZ() + 0.5;

        // Rising lava/ember fountain from the altar - taller each round.
        int height = Math.min(2 + round, 16);
        level.sendParticles(ParticleTypes.LAVA, ax, floorY + 1.0, az, 8 + round, 0.4, height * 0.15, 0.4, 0.1);
        level.sendParticles(ParticleTypes.FLAME, ax, floorY + 1.0 + height * 0.2, az, 12 + round, 0.5, height * 0.2, 0.5, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, ax, floorY + 3.0, az, 6, 1.0, 1.0, 1.0, 0.02);

        // Braziers flare.
        for (BlockPos b : AztecAbyssConstants.BRAZIER_POSITIONS) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5,
                    10 + round, 0.2, 0.6, 0.2, 0.03);
        }
        // Summit beacon roars.
        int topY = floorY + AztecAbyssConstants.TEMPLE_TIERS * AztecAbyssConstants.TEMPLE_TIER_HEIGHT + 2;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, 0.5, topY, 0.5, 20 + round * 2, 0.6, 1.0, 0.6, 0.05);
    }

    private static void maybeSpawnBats(ServerLevel level, RandomSource rng) {
        if (level.getGameTime() % 200 != 0 || rng.nextInt(3) != 0) {
            return;
        }
        long existing = level.getEntitiesOfClass(net.minecraft.world.entity.ambient.Bat.class,
                new net.minecraft.world.phys.AABB(-AztecAbyssConstants.ARENA_RADIUS, AztecAbyssConstants.ARENA_FLOOR_Y,
                        -AztecAbyssConstants.ARENA_RADIUS, AztecAbyssConstants.ARENA_RADIUS,
                        AztecAbyssConstants.ARENA_FLOOR_Y + AztecAbyssConstants.WALL_HEIGHT, AztecAbyssConstants.ARENA_RADIUS)).size();
        if (existing >= 8) {
            return;
        }
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = 30 + rng.nextDouble() * 50;
        BlockPos pos = new BlockPos((int) (Math.cos(angle) * dist),
                AztecAbyssConstants.ARENA_FLOOR_Y + 6 + rng.nextInt(10), (int) (Math.sin(angle) * dist));
        net.minecraft.world.entity.ambient.Bat bat = net.minecraft.world.entity.EntityType.BAT.create(level);
        if (bat != null) {
            bat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rng.nextFloat() * 360f, 0f);
            level.addFreshEntity(bat);
        }
    }
}
