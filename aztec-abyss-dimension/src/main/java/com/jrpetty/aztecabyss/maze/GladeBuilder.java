package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Glade: the ninety-six-block clearing the maze is wrapped around, and the
 * only ground in this world that is not trying to kill you.
 *
 * <p>It matters more than its size suggests. It is where every run starts and
 * ends, where you wait out the night, and the one place you ever stand still
 * long enough to look at anything - so a flat green square would undo whatever
 * the corridors achieve. It is laid out as a place people have been living in
 * for a while: the Box they came up in at the centre, a homestead, a worked
 * field, a wood, and a corner for the ones who did not come back.
 *
 * <p>Deterministic, like everything else here.
 */
public final class GladeBuilder {

    private static final int Y = MazeData.FLOOR_Y;

    private GladeBuilder() {
    }

    public static void build(ServerLevel level) {
        RandomSource rng = RandomSource.create(0x61ADE);
        ground(level, rng);
        theBox(level);
        homestead(level);
        field(level, rng);
        deadheads(level, rng);
        woods(level, rng);
        firepit(level);
        doorFrames(level);
    }

    private static int min() {
        return MazeData.gladeMinBlock();
    }

    private static int max() {
        return MazeData.gladeMaxBlock();
    }

    /** Grass, worn dirt paths, and the odd patch of long growth. */
    private static void ground(ServerLevel level, RandomSource rng) {
        int cx = MazeData.SPAWN_X;
        int cz = MazeData.SPAWN_Z;
        for (int x = min(); x <= max(); x++) {
            for (int z = min(); z <= max(); z++) {
                int h = Math.floorMod(x * 40503 ^ z * 26861, 100);
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));

                // A worn ring around the Box, and spokes out toward the doors.
                boolean path = Math.abs(d - 14) < 1.6
                        || (Math.abs(x - cx) <= 1 && d < 40)
                        || (Math.abs(z - cz) <= 1 && d < 40);
                BlockState top = path
                        ? (h < 45 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState())
                        : h < 6 ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState();
                level.setBlock(new BlockPos(x, Y, z), top, 2);

                if (!path && h >= 88) {
                    level.setBlock(new BlockPos(x, Y + 1, z),
                            h >= 96 ? Blocks.TALL_GRASS.defaultBlockState()
                                    : Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * The Box: the lift they all arrived in, sunk into the middle of the Glade
     * with its cage still standing open.
     */
    private static void theBox(ServerLevel level) {
        int cx = MazeData.SPAWN_X;
        int cz = MazeData.SPAWN_Z;
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean edge = x == cx - 3 || x == cx + 3 || z == cz - 3 || z == cz + 3;
                level.setBlock(new BlockPos(x, Y - 1, z),
                        edge ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                                : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y, z), edge
                        ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                if (!edge) {
                    continue;
                }
                // Cage bars, with the corners as posts.
                boolean corner = (x == cx - 3 || x == cx + 3) && (z == cz - 3 || z == cz + 3);
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(new BlockPos(x, Y + dy, z), corner
                            ? Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState()
                            : Blocks.IRON_BARS.defaultBlockState(), 2);
                }
                if (corner) {
                    level.setBlock(new BlockPos(x, Y + 5, z), Blocks.LANTERN.defaultBlockState(), 2);
                }
            }
        }
        // The way in, on the south face.
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(new BlockPos(x, Y + dy, cz + 3), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        sign(level, new BlockPos(cx, Y + 2, cz - 3), Direction.SOUTH,
                "§8— THE BOX —", "§7You came up", "§7in this.", "");
    }

    /** Three shacks and a store, built out of whatever came up in the Box. */
    private static void homestead(ServerLevel level) {
        hut(level, min() + 10, min() + 12, 9, 7);
        hut(level, min() + 10, min() + 24, 7, 6);
        hut(level, min() + 22, min() + 12, 6, 6);
    }

    /** One shack: log corners, plank walls, a stair roof and a lantern. */
    private static void hut(ServerLevel level, int ox, int oz, int w, int d) {
        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean edge = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                level.setBlock(new BlockPos(x, Y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                if (edge) {
                    for (int dy = 1; dy <= 3; dy++) {
                        level.setBlock(new BlockPos(x, Y + dy, z), corner
                                ? Blocks.OAK_LOG.defaultBlockState()
                                : Blocks.OAK_PLANKS.defaultBlockState(), 2);
                    }
                } else {
                    for (int dy = 1; dy <= 3; dy++) {
                        level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // Roof, pitched along the short axis.
                level.setBlock(new BlockPos(x, Y + 4, z),
                        Blocks.OAK_SLAB.defaultBlockState(), 2);
            }
        }
        // Doorway on the south wall, a window on the north.
        for (int dy = 1; dy <= 2; dy++) {
            level.setBlock(new BlockPos(ox + w / 2, Y + dy, oz + d - 1), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(ox + w / 2, Y + 2, oz), Blocks.OAK_FENCE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 3, oz + 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        level.setBlock(new BlockPos(ox + w - 2, Y + 1, oz + 1), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
    }

    /** The field they feed themselves from, with a water channel down the middle. */
    private static void field(ServerLevel level, RandomSource rng) {
        int ox = max() - 26;
        int oz = max() - 22;
        for (int x = ox; x < ox + 20; x++) {
            for (int z = oz; z < oz + 16; z++) {
                boolean channel = z == oz + 8;
                if (channel) {
                    level.setBlock(new BlockPos(x, Y, z), Blocks.WATER.defaultBlockState(), 2);
                    continue;
                }
                level.setBlock(new BlockPos(x, Y, z), Blocks.FARMLAND.defaultBlockState()
                        .setValue(BlockStateProperties.MOISTURE, 7), 2);
                int roll = rng.nextInt(10);
                BlockState crop = roll < 4 ? Blocks.WHEAT.defaultBlockState()
                        : roll < 6 ? Blocks.CARROTS.defaultBlockState()
                        : roll < 8 ? Blocks.POTATOES.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(x, Y + 1, z), crop, 2);
            }
        }
        // A fence and a gate so it reads as tended, not wild.
        for (int x = ox - 1; x <= ox + 20; x++) {
            level.setBlock(new BlockPos(x, Y + 1, oz - 1), Blocks.OAK_FENCE.defaultBlockState(), 2);
        }
    }

    /** The Deadheads: a dark grove where the ones who did not come back are buried. */
    private static void deadheads(ServerLevel level, RandomSource rng) {
        int ox = min() + 8;
        int oz = max() - 24;
        for (int i = 0; i < 14; i++) {
            int x = ox + rng.nextInt(18);
            int z = oz + rng.nextInt(16);
            level.setBlock(new BlockPos(x, Y, z), Blocks.PODZOL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, Y + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
            if (rng.nextInt(3) == 0) {
                level.setBlock(new BlockPos(x, Y + 2, z), Blocks.TORCH.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < 10; i++) {
            tree(level, ox + rng.nextInt(20), oz + rng.nextInt(18), true, rng);
        }
        sign(level, new BlockPos(ox + 9, Y + 1, oz - 1), Direction.SOUTH,
                "§8THE DEADHEADS", "§7They ran", "§7too.", "");
    }

    /** A wood along the north edge, for cover and for timber. */
    private static void woods(ServerLevel level, RandomSource rng) {
        for (int i = 0; i < 40; i++) {
            int x = min() + 4 + rng.nextInt(max() - min() - 8);
            int z = min() + 4 + rng.nextInt(24);
            double d = Math.sqrt(Math.pow(x - MazeData.SPAWN_X, 2) + Math.pow(z - MazeData.SPAWN_Z, 2));
            if (d < 20) {
                continue; // keep the middle clear
            }
            tree(level, x, z, rng.nextInt(4) == 0, rng);
        }
    }

    /** A simple hand-built tree - cheap, deterministic, and good enough at this scale. */
    private static void tree(ServerLevel level, int x, int z, boolean dark, RandomSource rng) {
        int h = 4 + rng.nextInt(3);
        BlockState log = dark ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
        BlockState leaf = dark ? Blocks.DARK_OAK_LEAVES.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState();
        for (int dy = 0; dy < h; dy++) {
            level.setBlock(new BlockPos(x, Y + 1 + dy, z), log, 2);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = h - 2; dy <= h + 1; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - h) > 3) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x + dx, Y + 1 + dy, z + dz);
                    if (level.getBlockState(at).isAir()) {
                        level.setBlock(at, leaf, 2);
                    }
                }
            }
        }
    }

    /** The fire everyone sits round when the doors shut. */
    private static void firepit(ServerLevel level) {
        int x = MazeData.SPAWN_X + 10;
        int z = MazeData.SPAWN_Z + 6;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 2) {
                    continue;
                }
                level.setBlock(new BlockPos(x + dx, Y, z + dz),
                        Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(x, Y + 1, z), Blocks.CAMPFIRE.defaultBlockState(), 2);
        for (int i = 0; i < 4; i++) {
            Direction d = Direction.from2DDataValue(i);
            level.setBlock(new BlockPos(x + d.getStepX() * 2, Y + 1, z + d.getStepZ() * 2),
                    Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, d), 2);
        }
    }

    /**
     * Monumental frames around the four Glade doors, so a sealed door reads as a
     * door rather than as more wall - which is the whole difference between the
     * Glade feeling enclosed and feeling walled in.
     */
    private static void doorFrames(ServerLevel level) {
        int[][] cells = {{48, 39}, {56, 48}, {47, 56}, {39, 47}};
        for (int[] cell : cells) {
            int bx = cell[0] * MazeData.CELL;
            int bz = cell[1] * MazeData.CELL;
            for (int lx = -1; lx <= MazeData.CELL; lx++) {
                for (int lz = -1; lz <= MazeData.CELL; lz++) {
                    boolean rim = lx == -1 || lx == MazeData.CELL || lz == -1 || lz == MazeData.CELL;
                    if (!rim) {
                        continue;
                    }
                    for (int dy = 0; dy <= 8; dy++) {
                        BlockPos at = new BlockPos(bx + lx, MazeData.WALL_BASE_Y + dy, bz + lz);
                        if (level.getBlockState(at).isAir()) {
                            continue; // never plug the doorway itself
                        }
                        level.setBlock(at, dy >= 7
                                ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                                : Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void sign(ServerLevel level, BlockPos pos, Direction facing,
                             String a, String b, String c, String d) {
        level.setBlock(pos, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            be.updateText(t -> t.setMessage(0, Component.literal(a))
                    .setMessage(1, Component.literal(b))
                    .setMessage(2, Component.literal(c))
                    .setMessage(3, Component.literal(d)), true);
        }
    }
}
