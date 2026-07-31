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
        // The second arena lives far along +X in the same dimension.
        BridgeBuilder.generateIfNeeded(level);

        BlockState sentinel = level.getBlockState(AztecAbyssConstants.TEMPLE_CENTER);
        if (sentinel.is(Blocks.GILDED_BLACKSTONE)) {
            // Arena is already standing, but a world generated before the gates
            // were boarded has arches with nothing behind them - and a free-standing
            // arch pens nothing, you just walk around it. Retro-fit the gatehouses
            // rather than making players delete the dimension to get the feature.
            BlockPos probe = AztecAbyssConstants.MOB_GATES[0]
                    .relative(com.jrpetty.aztecabyss.round.Barricade.outward(AztecAbyssConstants.MOB_GATES[0]),
                            com.jrpetty.aztecabyss.round.Barricade.POCKET_DEPTH);
            if (!level.getBlockState(probe.above()).is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
                buildMobGates(level);
            }
            return; // already built
        }

        carveFloorAndClearAir(level);
        buildPerimeterWall(level);
        decoratePerimeter(level);
        applyWorldBorder(level);
        TempleBuilder.build(level, AztecAbyssConstants.TEMPLE_CENTER);
        carveFloorVeins(level);
        placeObelisks(level);
        placeRuins(level);
        placeOreVeins(level);
        placeLootChests(level);
        placeArrivalPortal(level);
        buildMobGates(level);
        MonumentBuilder.build(level);
    }

    /**
     * The four horde gates: crying-obsidian arches at the cardinal points of
     * the wall, wreathed in soul fire. Every wave mob pours out of one of these
     * instead of materialising in the open, so the horde always has a visible
     * source players can watch (and dread).
     */
    private static void buildMobGates(ServerLevel level) {
        for (BlockPos gate : AztecAbyssConstants.MOB_GATES) {
            boolean onZAxis = gate.getX() == 0; // north/south gates span X; east/west span Z
            int gx = gate.getX();
            int gy = gate.getY();
            int gz = gate.getZ();
            for (int off = -2; off <= 2; off++) {
                int x = onZAxis ? gx + off : gx;
                int z = onZAxis ? gz : gz + off;
                boolean pillar = off == -2 || off == 2;
                for (int dy = 0; dy < 5; dy++) {
                    BlockPos p = new BlockPos(x, gy + dy, z);
                    if (pillar || dy == 4) {
                        level.setBlock(p, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
                    } else {
                        // Open mouth of the gate - kept as air so mobs walk straight out.
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                if (pillar) {
                    level.setBlock(new BlockPos(x, gy + 5, z), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
                    // Flanking towers so each gate reads as a monumental doorway.
                    for (int dy = 5; dy <= 9; dy++) {
                        level.setBlock(new BlockPos(x, gy + dy, z),
                                dy == 9 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                    }
                    level.setBlock(new BlockPos(x, gy + 10, z), Blocks.SOUL_SOIL.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x, gy + 11, z), Blocks.SOUL_FIRE.defaultBlockState(), 3);
                }
            }
            // Scorched threshold, glowing hot at the mouth of the gate.
            for (int off = -3; off <= 3; off++) {
                int x = onZAxis ? gx + off : gx;
                int z = onZAxis ? gz : gz + off;
                level.setBlock(new BlockPos(x, gy - 1, z),
                        Math.abs(off) <= 1 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : Blocks.BLACKSTONE.defaultBlockState(), 3);
                // Scorch fanning out into the arena from the mouth.
                for (int step = 1; step <= 4; step++) {
                    int sx = onZAxis ? x : gx + (gx > 0 ? -step : step);
                    int sz = onZAxis ? gz + (gz > 0 ? -step : step) : z;
                    if (Math.abs(off) <= 3 - step / 2) {
                        level.setBlock(new BlockPos(sx, gy - 1, sz), Blocks.BLACKSTONE.defaultBlockState(), 2);
                    }
                }
            }
            // Hanging chains flanking the arch. They sit on the pillar line rather
            // than in the mouth itself, because the mouth belongs to the boards.
            for (int off = -2; off <= 2; off += 4) {
                int x = onZAxis ? gx + off : gx;
                int z = onZAxis ? gz : gz + off;
                level.setBlock(new BlockPos(x, gy + 3, z), Blocks.CHAIN.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 3);
            }

            buildGatehouse(level, gate);
        }
    }

    /**
     * The sealed pen behind each arch that the horde spawns into.
     *
     * <p>This is what turns the boards from decoration into a mechanic. The arch
     * on its own is a free-standing structure in an open field - anything penned
     * by it would simply walk around. So each gate gets a roofed, walled
     * gatehouse whose only way out is the boarded mouth: no flanking it, no
     * climbing it (spiders), no flying over it (phantoms). Everything that
     * reaches the arena comes through the boards.
     *
     * <p>It doubles as atmosphere - you can see them massing in the dark behind
     * the planks, lit from below by soul fire, before a single board comes off.
     */
    private static void buildGatehouse(ServerLevel level, BlockPos gate) {
        boolean onZAxis = gate.getX() == 0;
        Direction out = com.jrpetty.aztecabyss.round.Barricade.outward(gate);
        int half = com.jrpetty.aztecabyss.round.Barricade.POCKET_HALF_WIDTH;
        int depth = com.jrpetty.aztecabyss.round.Barricade.POCKET_DEPTH;
        int gy = gate.getY();

        BlockState shell = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.GILDED_BLACKSTONE.defaultBlockState();

        for (int d = 0; d <= depth; d++) {
            BlockPos row = gate.relative(out, d);
            for (int off = -half; off <= half; off++) {
                int x = onZAxis ? row.getX() + off : row.getX();
                int z = onZAxis ? row.getZ() : row.getZ() + off;

                // Floor: scorched ground the whole way back.
                level.setBlock(new BlockPos(x, gy - 1, z), Blocks.BLACKSTONE.defaultBlockState(), 3);

                boolean sideWall = Math.abs(off) == half;
                boolean backWall = d == depth;
                if (d > 0 && (sideWall || backWall)) {
                    for (int dy = 0; dy < 6; dy++) {
                        level.setBlock(new BlockPos(x, gy + dy, z), shell, 3);
                    }
                } else if (d > 0) {
                    // Hollow interior - clear whatever the arena floor left behind.
                    for (int dy = 0; dy < 5; dy++) {
                        level.setBlock(new BlockPos(x, gy + dy, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                // Roof. Started one block out so it never eats the arch or its towers.
                if (d > 0) {
                    level.setBlock(new BlockPos(x, gy + 5, z), d == depth || sideWall ? trim : shell, 3);
                }
            }

            // Seal the strip directly above the lintel, where the arch stops and
            // the pen has not started - otherwise there is a slot to fly out of.
            if (d == 0) {
                for (int off = -half; off <= half; off++) {
                    int x = onZAxis ? row.getX() + off : row.getX();
                    int z = onZAxis ? row.getZ() : row.getZ() + off;
                    level.setBlock(new BlockPos(x, gy + 5, z), shell, 3);
                }
            }
        }

        // Soul fire in the back corners: they arrive out of the dark, underlit.
        for (int off = -half + 1; off <= half - 1; off += (half - 1) * 2) {
            BlockPos back = gate.relative(out, depth - 1);
            int x = onZAxis ? back.getX() + off : back.getX();
            int z = onZAxis ? back.getZ() : back.getZ() + off;
            level.setBlock(new BlockPos(x, gy - 1, z), Blocks.SOUL_SOIL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, gy, z), Blocks.SOUL_FIRE.defaultBlockState(), 3);
        }
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

        // The flat generator already leaves air above the base layer, so we don't
        // clear a tall air column (that was ~1.4M redundant setBlock calls). We just
        // lay a 2-block-deep floor: a surface + one bedrock layer beneath it. There's
        // no digging in this mode, so nothing deeper needs to exist.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                level.setBlock(new BlockPos(x, floorY, z), floorMaterial(rng), 2);
                level.setBlock(new BlockPos(x, floorY - 1, z), Blocks.BEDROCK.defaultBlockState(), 2);
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
        int thickness = 2; // world border does the real containment; wall is the visual boundary

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

    /**
     * Scatters crumbling Aztec ruins across the open field around the temple -
     * broken walls, toppled pillars, rubble piles and the odd arch. Deliberately
     * low and sparse so sightlines stay open and the horde can always path
     * through: cover and character without the sightline-blocking forest that
     * used to stand here.
     */
    private static void placeRuins(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 1);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int innerEdge = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 8;
        int outerEdge = AztecAbyssConstants.ARENA_RADIUS - 10;

        for (int i = 0; i < 46; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = innerEdge + rng.nextDouble() * (outerEdge - innerEdge);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);

            // Keep the arrival walkway and every gate mouth clear.
            if (z > 40 && Math.abs(x) < 6) {
                continue;
            }
            if (nearGate(x, z, 7)) {
                continue;
            }
            BlockPos base = new BlockPos(x, floorY + 1, z);
            switch (rng.nextInt(4)) {
                case 0 -> ruinWall(level, base, rng);
                case 1 -> ruinPillar(level, base, rng);
                case 2 -> ruinRubble(level, base, rng);
                default -> ruinArch(level, base, rng);
            }
        }
    }

    /**
     * Glowing veins clawing outward from the temple across the arena floor:
     * crying-obsidian and magma cracks fading into blackstone scorch. Gives the
     * flat field depth and a sense that something under it is still alive.
     */
    private static void carveFloorVeins(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 11);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int start = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 2;
        int end = AztecAbyssConstants.ARENA_RADIUS - 4;

        int veins = 14;
        for (int v = 0; v < veins; v++) {
            double angle = (Math.PI * 2.0 / veins) * v + rng.nextDouble() * 0.25;
            double drift = 0.0;
            for (int d = start; d < end; d++) {
                // Wander a little so the vein snakes instead of running straight.
                drift += (rng.nextDouble() - 0.5) * 0.16;
                double a = angle + drift;
                int x = (int) Math.round(Math.cos(a) * d);
                int z = (int) Math.round(Math.sin(a) * d);

                // Brightest near the temple, guttering out toward the wall.
                double t = 1.0 - (d - start) / (double) (end - start);
                BlockState core;
                double roll = rng.nextDouble();
                if (roll < 0.18 * t) {
                    core = Blocks.MAGMA_BLOCK.defaultBlockState();
                } else if (roll < 0.45 * t) {
                    core = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                } else if (roll < 0.75) {
                    core = Blocks.BLACKSTONE.defaultBlockState();
                } else {
                    core = Blocks.BASALT.defaultBlockState();
                }
                level.setBlock(new BlockPos(x, floorY, z), core, 2);

                // Ragged edges either side of the crack.
                if (rng.nextInt(3) == 0) {
                    int ox = x + (rng.nextBoolean() ? 1 : -1);
                    int oz = z + (rng.nextBoolean() ? 1 : -1);
                    level.setBlock(new BlockPos(ox, floorY, oz), Blocks.BLACKSTONE.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * A ring of tall carved obelisks standing guard between the temple and the
     * outer field - vertical punctuation on an otherwise flat arena, each capped
     * with a burning soul-flame.
     */
    private static void placeObelisks(ServerLevel level) {
        Random rng = new Random(WORLDGEN_SEED + 12);
        int floorY = AztecAbyssConstants.ARENA_FLOOR_Y;
        int ring = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 12;
        int count = 8;

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i + Math.PI / count;
            int x = (int) Math.round(Math.cos(angle) * ring);
            int z = (int) Math.round(Math.sin(angle) * ring);
            if (nearGate(x, z, 8)) {
                continue;
            }
            int h = 7 + rng.nextInt(4);
            for (int dy = 1; dy <= h; dy++) {
                BlockState body = (dy % 3 == 0)
                        ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                level.setBlock(new BlockPos(x, floorY + dy, z), body, 2);
            }
            // Glyph band and a soul-flame crown.
            level.setBlock(new BlockPos(x, floorY + h - 2, z), Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, floorY + h + 1, z), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, floorY + h + 2, z), Blocks.SOUL_FIRE.defaultBlockState(), 2);
            // Cracked base spilling around the foot.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (rng.nextBoolean()) {
                        level.setBlock(new BlockPos(x + dx, floorY + 1, z + dz),
                                Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static boolean nearGate(int x, int z, int pad) {
        for (BlockPos g : AztecAbyssConstants.MOB_GATES) {
            if (Math.abs(g.getX() - x) <= pad && Math.abs(g.getZ() - z) <= pad) {
                return true;
            }
        }
        return false;
    }

    private static BlockState ruinBlock(Random rng) {
        return switch (rng.nextInt(5)) {
            case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case 2 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case 3 -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /** A broken length of wall, taller at one end and crumbling away at the other. */
    private static void ruinWall(ServerLevel level, BlockPos base, Random rng) {
        boolean alongX = rng.nextBoolean();
        int len = 4 + rng.nextInt(6);
        int peak = 2 + rng.nextInt(3);
        for (int i = 0; i < len; i++) {
            int h = Math.max(1, peak - (i * peak) / Math.max(1, len - 1) + (rng.nextInt(2)));
            for (int dy = 0; dy < h; dy++) {
                BlockPos p = alongX ? base.offset(i, dy, 0) : base.offset(0, dy, i);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, ruinBlock(rng), 2);
                }
            }
            // The odd gap where the wall has fallen through.
            if (rng.nextInt(5) == 0) {
                i++;
            }
        }
    }

    /** A toppled or snapped-off column, sometimes with a fallen capital beside it. */
    private static void ruinPillar(ServerLevel level, BlockPos base, Random rng) {
        int h = 2 + rng.nextInt(4);
        for (int dy = 0; dy < h; dy++) {
            BlockPos p = base.above(dy);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, ruinBlock(rng), 2);
            }
        }
        if (rng.nextBoolean()) {
            level.setBlock(base.above(h), Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
        }
        // Fallen section lying on the ground.
        if (rng.nextInt(3) == 0) {
            int dir = rng.nextInt(4);
            for (int i = 1; i <= 2 + rng.nextInt(2); i++) {
                BlockPos p = switch (dir) {
                    case 0 -> base.offset(i, 0, 0);
                    case 1 -> base.offset(-i, 0, 0);
                    case 2 -> base.offset(0, 0, i);
                    default -> base.offset(0, 0, -i);
                };
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, ruinBlock(rng), 2);
                }
            }
        }
    }

    /** A low scatter of rubble and slabs - ankle-height dressing. */
    private static void ruinRubble(ServerLevel level, BlockPos base, Random rng) {
        int spread = 2 + rng.nextInt(3);
        for (int dx = -spread; dx <= spread; dx++) {
            for (int dz = -spread; dz <= spread; dz++) {
                if (rng.nextDouble() > 0.35) {
                    continue;
                }
                BlockPos p = base.offset(dx, 0, dz);
                if (!level.getBlockState(p).isAir()) {
                    continue;
                }
                level.setBlock(p, rng.nextBoolean()
                        ? Blocks.STONE_BRICK_SLAB.defaultBlockState()
                        : ruinBlock(rng), 2);
            }
        }
    }

    /** A standing doorway/arch - the most recognisable ruin silhouette. */
    private static void ruinArch(ServerLevel level, BlockPos base, Random rng) {
        boolean alongX = rng.nextBoolean();
        int h = 3 + rng.nextInt(2);
        int span = 2 + rng.nextInt(2);
        for (int dy = 0; dy < h; dy++) {
            BlockPos a = base.above(dy);
            BlockPos b = alongX ? base.offset(span, dy, 0) : base.offset(0, dy, span);
            if (level.getBlockState(a).isAir()) {
                level.setBlock(a, ruinBlock(rng), 2);
            }
            if (level.getBlockState(b).isAir()) {
                level.setBlock(b, ruinBlock(rng), 2);
            }
        }
        // Lintel across the top, sometimes partly collapsed.
        for (int i = 0; i <= span; i++) {
            if (rng.nextInt(6) == 0) {
                continue;
            }
            BlockPos p = alongX ? base.offset(i, h, 0) : base.offset(0, h, i);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, ruinBlock(rng), 2);
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

        int oreSpan = AztecAbyssConstants.ARENA_RADIUS - 8 - (AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 4);
        for (int i = 0; i < 40; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 4 + rng.nextDouble() * oreSpan;
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

        int chestSpan = AztecAbyssConstants.ARENA_RADIUS - 8 - (AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 6);
        while (placed < 6 && attempts < 200) {
            attempts++;
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = AztecAbyssConstants.TEMPLE_BASE_HALF_WIDTH + 6 + rng.nextDouble() * chestSpan;
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
                net.minecraft.world.item.Items.GOLDEN_CARROT,
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
                    level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
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
        // Temple dressing only - don't burn particles and entity queries on an
        // arena nobody is standing in.
        if (com.jrpetty.aztecabyss.round.RoundManager.game().getMap()
                != com.jrpetty.aztecabyss.worldgen.ArenaMap.TEMPLE) {
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
        // The horde gates smoulder with soul energy so they always read as live.
        for (BlockPos gate : AztecAbyssConstants.MOB_GATES) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    gate.getX() + 0.5, gate.getY() + 1.5, gate.getZ() + 0.5, 2, 1.2, 1.0, 1.2, 0.01);
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
        double dist = 30 + rng.nextDouble() * (AztecAbyssConstants.ARENA_RADIUS - 37);
        BlockPos pos = new BlockPos((int) (Math.cos(angle) * dist),
                AztecAbyssConstants.ARENA_FLOOR_Y + 6 + rng.nextInt(10), (int) (Math.sin(angle) * dist));
        net.minecraft.world.entity.ambient.Bat bat = net.minecraft.world.entity.EntityType.BAT.create(level);
        if (bat != null) {
            bat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rng.nextFloat() * 360f, 0f);
            level.addFreshEntity(bat);
        }
    }
}
