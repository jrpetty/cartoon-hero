package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        mapRoom(level);
        jobBoard(level);
        bellTower(level);
        MazeStations.build(level);
        doorFrames(level);
        // Last, and it clears its own airspace first, exactly as the map floor
        // it replaces did: the woods scatter trees across this quarter and
        // anything laid under them without clearing would keep forty floating
        // canopies.
        lakeside(level, rng);
    }

    /**
     * The lake, where the Chart Floor used to be.
     *
     * <p>The walk-on mosaic is gone - the Runners carry a real chart now, and a
     * map you hold beats a map you stand on. What the south-west quarter gets
     * back is the one thing the Glade never had: somewhere that is not work. A
     * kidney of open water, reeds on the shore, a birch-less little wood of its
     * own, and a log to sit on facing the water. Camps have a spot like this or
     * they are barracks.
     */
    private static void lakeside(ServerLevel level, RandomSource rng) {
        int o = min() + 1;
        int size = 43;
        clearFor(level, o - 1, o - 1, size + 2);
        // Fresh turf over the old plaza bed, three deep like everywhere else.
        for (int x = o; x < o + size; x++) {
            for (int z = o; z < o + size; z++) {
                int h = Math.floorMod(x * 40503 ^ z * 26861, 100);
                level.setBlock(new BlockPos(x, Y, z), h < 8
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y - 2, z), Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        // The water: two overlapping rounds so the shore wobbles.
        int c1x = o + 18, c1z = o + 15, c2x = o + 26, c2z = o + 22;
        for (int x = o; x < o + size; x++) {
            for (int z = o; z < o + size; z++) {
                double d1 = Math.sqrt((x - c1x) * (x - c1x) + (double) (z - c1z) * (z - c1z));
                double d2 = Math.sqrt((x - c2x) * (x - c2x) + (double) (z - c2z) * (z - c2z));
                double d = Math.min(d1, d2) + rng.nextDouble() * 0.8;
                if (d < 7.0) {
                    level.setBlock(new BlockPos(x, Y, z), Blocks.WATER.defaultBlockState(), 2);
                    // A shallow bowl: deeper in the middle, dirt underneath.
                    level.setBlock(new BlockPos(x, Y - 1, z), d < 4.5
                            ? Blocks.WATER.defaultBlockState()
                            : Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, Y - 2, z), Blocks.DIRT.defaultBlockState(), 2);
                } else if (d < 8.4 && rng.nextInt(3) == 0) {
                    // Reeds where the ground still touches water.
                    int stalks = 2 + rng.nextInt(2);
                    for (int dy = 1; dy <= stalks; dy++) {
                        level.setBlock(new BlockPos(x, Y + dy, z),
                                Blocks.SUGAR_CANE.defaultBlockState(), 2);
                    }
                }
            }
        }
        // Its own stand of trees, kept off the water.
        for (int i = 0; i < 12; i++) {
            int x = o + 2 + rng.nextInt(size - 4);
            int z = o + 2 + rng.nextInt(size - 4);
            double d1 = Math.sqrt((x - c1x) * (x - c1x) + (double) (z - c1z) * (z - c1z));
            double d2 = Math.sqrt((x - c2x) * (x - c2x) + (double) (z - c2z) * (z - c2z));
            if (Math.min(d1, d2) > 10.0) {
                tree(level, x, z, rng.nextInt(4) == 0, rng);
            }
        }
        // A log to sit on, facing the water.
        level.setBlock(new BlockPos(c1x - 9, Y + 1, c1z - 2), Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(new BlockPos(c1x - 9, Y + 1, c1z - 1), Blocks.OAK_LOG.defaultBlockState(), 2);
        // Long grass and the odd poppy, because tended-wild reads warmer than mown.
        for (int i = 0; i < 40; i++) {
            int x = o + rng.nextInt(size);
            int z = o + rng.nextInt(size);
            BlockPos at = new BlockPos(x, Y + 1, z);
            if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK)) {
                level.setBlock(at, rng.nextInt(8) == 0
                        ? Blocks.POPPY.defaultBlockState()
                        : rng.nextInt(3) == 0 ? Blocks.TALL_GRASS.defaultBlockState()
                        : Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
        }
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
                // Three deep. The clearing was a single block of turf laid over
                // a six-block drop to bedrock - nothing under the grass at all,
                // which is why the field's water channel had nothing to sit on
                // and why every cut edge in here showed void rather than soil.
                // Grass on top, two of dirt beneath it, the way ground works.
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y - 2, z), Blocks.DIRT.defaultBlockState(), 2);

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

    /**
     * Three shacks and a store, built out of whatever came up in the Box.
     *
     * <p>Moved to the east side to make room for the Chart Floor. The homestead
     * can be anywhere; the map wants the biggest uninterrupted square in the
     * clearing, and there is exactly one of those.
     */
    private static void homestead(ServerLevel level) {
        int ox = MazeData.SPAWN_X + 13;
        hut(level, ox, min() + 5, 9, 7);
        hut(level, ox, min() + 17, 7, 6);
        hut(level, ox + 12, min() + 5, 6, 6);
    }

    /**
     * Empties a square down to the ground so something can be laid on it.
     *
     * <p>The Glade is built in passes and the later passes have no idea what the
     * earlier ones put down. Anything that wants a clear footprint has to say so.
     */
    private static void clearFor(ServerLevel level, int ox, int oz, int size) {
        for (int x = ox; x < ox + size; x++) {
            for (int z = oz; z < oz + size; z++) {
                for (int dy = 1; dy <= 12; dy++) {
                    level.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Is there nothing standing here? Used before anything is scattered. */
    private static boolean clearColumn(ServerLevel level, int x, int z, int height) {
        for (int dy = 1; dy <= height; dy++) {
            BlockState at = level.getBlockState(new BlockPos(x, Y + dy, z));
            // Named rather than asked, because the ground pass is the only thing
            // that puts anything here and it puts exactly these three. Anything
            // else standing in this column is a building.
            if (at.isAir()
                    || at.is(Blocks.SHORT_GRASS)
                    || at.is(Blocks.TALL_GRASS)
                    || at.is(Blocks.POPPY)) {
                continue;
            }
            return false;
        }
        return true;
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
            }
        }
        roof(level, ox, oz, w, d);
        // Doorway on the south wall, a window on the north.
        for (int dy = 1; dy <= 2; dy++) {
            level.setBlock(new BlockPos(ox + w / 2, Y + dy, oz + d - 1), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(ox + w / 2, Y + 2, oz), Blocks.OAK_FENCE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 3, oz + 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        furnish(level, ox, oz, w, d);
        // A bed against the west wall. Set after the furniture so a hut always
        // ends up with one whatever else the furnishing put down - the night
        // can be slept through now, and a bed you have to build first makes
        // the first exhausted night a scavenger hunt.
        var bed = Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING,
                        net.minecraft.core.Direction.NORTH);
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + 2), bed.setValue(
                net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + 1), bed.setValue(
                net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
    }

    /**
     * A gable, rather than the flat slab lid this used to have.
     *
     * <p>The old code laid one layer of slabs across the whole footprint under a
     * comment that said "pitched along the short axis". It was not pitched at
     * all, and three identical boxes with flat tops is what the homestead looked
     * like from every angle in the Glade.
     *
     * <p>Stairs up both slopes to a slab ridge, with a one-block overhang all
     * round so the eaves cast a line down the walls. The overhang is most of why
     * a pitched roof reads as a roof rather than as a triangle.
     */
    private static void roof(ServerLevel level, int ox, int oz, int w, int d) {
        // Each step inward from the eaves is one course higher. The ridge is
        // wherever the two slopes meet, which for an even depth is two blocks
        // wide and for an odd depth is one.
        int peak = d / 2 + 1;
        for (int x = ox - 1; x <= ox + w; x++) {
            for (int step = 0; step <= peak; step++) {
                int y = Y + 4 + step;
                int zNorth = oz - 1 + step;
                int zSouth = oz + d - step;
                if (zNorth >= zSouth) {
                    for (int z = zSouth; z <= zNorth; z++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.OAK_SLAB.defaultBlockState(), 2);
                    }
                    break;
                }
                level.setBlock(new BlockPos(x, y, zNorth), Blocks.OAK_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 2);
                level.setBlock(new BlockPos(x, y, zSouth), Blocks.OAK_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
                // Gable ends filled in, so the roof has a wall under it rather
                // than a view straight through the rafters and out the far side.
                if (x == ox || x == ox + w - 1) {
                    for (int z = zNorth + 1; z < zSouth; z++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * Something inside, because an empty shack is a shed.
     *
     * <p>Three huts stood open and completely bare apart from one crafting
     * table. The Glade is supposed to read as a place people have lived in for
     * a year - a pallet somebody sleeps on, somewhere to put things, a pot by
     * the door. None of it is functional and all of it is the difference
     * between a settlement and a set of boxes.
     */
    private static void furnish(ServerLevel level, int ox, int oz, int w, int d) {
        // A pallet in the corner. Hay and wool rather than a bed: beds do not
        // work in this dimension and a bed you cannot sleep in is a promise the
        // Glade breaks every night.
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + 1), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 2, oz + 1), Blocks.WHITE_CARPET.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 2, oz + 2), Blocks.WHITE_CARPET.defaultBlockState(), 2);

        // A bench and a place to work.
        level.setBlock(new BlockPos(ox + w - 2, Y + 1, oz + 1), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + w - 2, Y + 1, oz + 2), Blocks.BARREL.defaultBlockState(), 2);

        // A rug down the middle, and a pot by the door.
        int mid = oz + d / 2;
        for (int x = ox + 2; x <= ox + w - 3; x++) {
            level.setBlock(new BlockPos(x, Y + 1, mid), Blocks.BROWN_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(ox + w - 2, Y + 1, oz + d - 2), Blocks.FLOWER_POT.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz + d - 2), Blocks.COMPOSTER.defaultBlockState(), 2);
    }

    public static final int FIELD_W = 20;
    public static final int FIELD_D = 16;

    public static int fieldX() {
        return MazeData.gladeMaxBlock() - 26;
    }

    public static int fieldZ() {
        return MazeData.gladeMaxBlock() - 22;
    }

    /** The field they feed themselves from, with a water channel down the middle. */
    private static void field(ServerLevel level, RandomSource rng) {
        int ox = fieldX();
        int oz = fieldZ();
        for (int x = ox; x < ox + FIELD_W; x++) {
            for (int z = oz; z < oz + FIELD_D; z++) {
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
                // Sown at mixed ages, some of it ready. A field that starts at
                // age zero everywhere is a field nobody can work on day one, and
                // "the Gladers have been here a while" is the whole premise.
                if (crop.getBlock() instanceof net.minecraft.world.level.block.CropBlock cb) {
                    crop = cb.getStateForAge(rng.nextInt(cb.getMaxAge() + 1));
                }
                level.setBlock(new BlockPos(x, Y + 1, z), crop, 2);
            }
        }
        // A fence all the way round, with a gate you actually walk through.
        //
        // This used to be one row along the north edge under a comment claiming
        // "a fence and a gate" - there was no gate, and three sides of the field
        // simply ran out into the grass. A fence on one side does not read as
        // tended; it reads as an unfinished fence.
        for (int x = ox - 1; x <= ox + FIELD_W; x++) {
            level.setBlock(new BlockPos(x, Y + 1, oz - 1), Blocks.OAK_FENCE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, Y + 1, oz + FIELD_D), Blocks.OAK_FENCE.defaultBlockState(), 2);
        }
        for (int z = oz - 1; z <= oz + FIELD_D; z++) {
            level.setBlock(new BlockPos(ox - 1, Y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(ox + FIELD_W, Y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
        }
        // The gate, on the side facing the Box, with a lantern either post.
        int gate = ox + FIELD_W / 2;
        level.setBlock(new BlockPos(gate, Y + 1, oz - 1), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 2);
        level.setBlock(new BlockPos(gate - 1, Y + 2, oz - 1), Blocks.LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(gate + 1, Y + 2, oz - 1), Blocks.LANTERN.defaultBlockState(), 2);
        // A composter and a water barrel at the gate: the field should look like
        // somewhere people come to work rather than a rectangle of farmland.
        level.setBlock(new BlockPos(ox, Y + 1, oz - 2), Blocks.COMPOSTER.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 1, Y + 1, oz - 2), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + FIELD_W - 1, Y + 1, oz - 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        sign(level, new BlockPos(gate + 2, Y + 1, oz - 1), Direction.NORTH,
                "§2THE FIELD", "§7Everything", "§7we eat.", "");
    }

    /**
     * The field comes on overnight.
     *
     * <p>Not decoration and not a convenience. Vanilla crop growth needs light
     * and a random tick, and this is a bespoke dimension with a barrier lid over
     * most of it - relying on the vanilla rules here would mean the Track-hoe's
     * job quietly did not exist on some worlds and did on others, which is the
     * worst kind of bug because it looks like bad luck. Growing the field
     * explicitly at dawn makes the job work the same way everywhere.
     *
     * <p>It also gives the day counter something to be. A day in the Glade is
     * now a day the field moved, whether or not anybody ran the maze.
     */
    public static void growField(ServerLevel level, RandomSource rng, int greenThumb) {
        int ox = fieldX();
        int oz = fieldZ();
        for (int x = ox; x < ox + FIELD_W; x++) {
            for (int z = oz; z < oz + FIELD_D; z++) {
                BlockPos at = new BlockPos(x, Y + 1, z);
                BlockState state = level.getBlockState(at);
                if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop)) {
                    // Harvested ground reseeds itself. Somebody planted this field
                    // long before you arrived and it is not going to stop because
                    // the last Glader forgot to put a seed back.
                    if (state.isAir() && rng.nextInt(Math.max(1, 4 - greenThumb)) == 0
                            && level.getBlockState(at.below()).is(Blocks.FARMLAND)) {
                        int roll = rng.nextInt(3);
                        level.setBlock(at, roll == 0 ? Blocks.WHEAT.defaultBlockState()
                                : roll == 1 ? Blocks.CARROTS.defaultBlockState()
                                : Blocks.POTATOES.defaultBlockState(), 2);
                    }
                    continue;
                }
                if (crop.isMaxAge(state)) {
                    continue;
                }
                // growCrops rather than setting the age property directly: beetroot
                // and wheat do not share an age range, and the block already knows
                // its own. Twice, so a day is a visible step rather than a nudge.
                crop.growCrops(level, at, state);
                crop.growCrops(level, at, level.getBlockState(at));
                // Green Thumb: the field comes on further under a good farmer.
                for (int extra = 0; extra < greenThumb; extra++) {
                    BlockState now = level.getBlockState(at);
                    if (!(now.getBlock() instanceof net.minecraft.world.level.block.CropBlock c2)
                            || c2.isMaxAge(now)) {
                        break;
                    }
                    c2.growCrops(level, at, now);
                }
            }
        }
    }

    /** The Deadheads: a dark grove where the ones who did not come back are buried. */
    private static void deadheads(ServerLevel level, RandomSource rng) {
        int ox = min() + 8;
        int oz = max() - 24;
        for (int i = 0; i < 14; i++) {
            int x = ox + rng.nextInt(18);
            int z = oz + rng.nextInt(16);
            if (!clearColumn(level, x, z, 3)) {
                continue;
            }
            // A grave rather than a lone wall block: turned earth around it, a
            // headstone, and about a third of them still tended with a light or
            // something growing. Fourteen identical cobble stubs in a podzol
            // patch read as scenery; this reads as somewhere people go.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getBlockState(new BlockPos(x + dx, Y, z + dz)).is(Blocks.GRASS_BLOCK)) {
                        level.setBlock(new BlockPos(x + dx, Y, z + dz),
                                Blocks.PODZOL.defaultBlockState(), 2);
                    }
                }
            }
            level.setBlock(new BlockPos(x, Y, z), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, Y + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
            int tended = rng.nextInt(3);
            if (tended == 0) {
                level.setBlock(new BlockPos(x, Y + 2, z), Blocks.TORCH.defaultBlockState(), 2);
            } else if (tended == 1 && clearColumn(level, x, z + 1, 2)) {
                level.setBlock(new BlockPos(x, Y + 1, z + 1),
                        Blocks.POPPY.defaultBlockState(), 2);
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

    /**
     * A simple hand-built tree - cheap, deterministic, and good enough at this
     * scale.
     *
     * <p>The leaves are placed <em>persistent</em>. A hand-built tree is not a
     * grown one: {@code setBlock} with no neighbour update leaves every leaf at
     * distance seven, which is precisely vanilla's decaying condition, so the
     * whole wood would quietly random-tick itself away to forty bare trunks over
     * the first day. That was survivable while the wood was only scenery and
     * timber. It is not survivable now the wood is the <em>only</em> source of
     * apples, and therefore the only route to a golden apple.
     */
    private static void tree(ServerLevel level, int x, int z, boolean dark, RandomSource rng) {
        // Nothing plants itself through a roof. The woods are scattered after
        // the homestead is up and the two passes know nothing about each other,
        // so trees were growing out of the huts - trunk through the floor,
        // canopy through the ridge.
        if (!clearColumn(level, x, z, 8)) {
            return;
        }
        int h = 4 + rng.nextInt(3);
        BlockState log = dark ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
        BlockState leaf = (dark ? Blocks.DARK_OAK_LEAVES : Blocks.OAK_LEAVES).defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true);
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

    /**
     * The Map Room.
     *
     * <p>Everything else in the Glade is scenery - a homestead, huts, a field, a
     * firepit, none of which a player can do anything with. This is the one
     * building with a job: it is where what the Runners brought back is kept, and
     * the only place the maze can be looked at rather than walked.
     *
     * <p>Deliberately the most solid thing in the clearing. Deepslate and a lit
     * interior, because it is the one structure the Gladers would actually have
     * built to last rather than thrown together.
     */
    private static void mapRoom(ServerLevel level) {
        int ox = MazeData.SPAWN_X + 10;
        int oz = MazeData.SPAWN_Z - 12;
        int y = MazeData.FLOOR_Y + 1;
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                for (int dy = 0; dy < 4; dy++) {
                    BlockPos at = new BlockPos(ox + dx, y + dy, oz + dz);
                    if (dy == 3) {
                        level.setBlock(at, Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
                    } else if (edge) {
                        level.setBlock(at, wall, 2);
                    } else {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                level.setBlock(new BlockPos(ox + dx, y - 1, oz + dz),
                        Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        // A doorway facing the middle of the Glade, so it is walked into rather
        // than found.
        for (int dy = 0; dy < 2; dy++) {
            level.setBlock(new BlockPos(ox, y + dy, oz + 3), Blocks.AIR.defaultBlockState(), 2);
        }
        // The table itself, and light to read it by.
        level.setBlock(new BlockPos(ox + 3, y, oz + 3), Blocks.LECTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(ox + 3, y + 2, oz + 3), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        sign(level, new BlockPos(ox + 1, y + 1, oz + 3), Direction.WEST,
                "§0THE MAP ROOM", "§0/maze map", "", "");
    }

    /**
     * The Job Board: four posts by the Box, one for each trade.
     *
     * <p>Put deliberately where everyone lands rather than tucked away with the
     * Map Room. A job you have to go looking for is a job nobody takes, and the
     * first thing a new Glader should learn is that there is something for them
     * to be.
     */
    private static void jobBoard(ServerLevel level) {
        int ox = MazeData.SPAWN_X - 8;
        int oz = MazeData.SPAWN_Z + 5;
        String[][] posts = {
                {"§1RUNNER", "§8the corridors", "", "§0» right-click «"},
                {"§6BUILDER", "§8the forge", "", "§0» right-click «"},
                {"§2MED-JACK", "§8the stung", "", "§0» right-click «"},
                {"§0TRACK-HOE", "§8the field", "", "§0» right-click «"},
        };
        // Logs rather than fences for the posts: a wall sign wants a solid face
        // behind it, and a fence is not one - the whole board would pop off the
        // first time a neighbour updated.
        for (int i = 0; i < posts.length; i++) {
            int x = ox + i * 3;
            level.setBlock(new BlockPos(x, Y, oz), Blocks.COBBLESTONE.defaultBlockState(), 2);
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(new BlockPos(x, Y + dy, oz), Blocks.OAK_LOG.defaultBlockState(), 2);
            }
            // A lantern standing on top of each post, so the board reads at night.
            level.setBlock(new BlockPos(x, Y + 4, oz), Blocks.LANTERN.defaultBlockState(), 2);
            sign(level, new BlockPos(x, Y + 2, oz + 1), Direction.SOUTH,
                    posts[i][0], posts[i][1], posts[i][2], posts[i][3]);
            // A second sign under each post for the roster. Written blank here
            // and filled in by refreshRoster once there is anybody to list.
            sign(level, new BlockPos(x, Y + 1, oz + 1), Direction.SOUTH,
                    "§8nobody", "", "", "");
        }
    }

    /** Where a trade's roster sign hangs, in job-board order. */
    private static BlockPos rosterSign(int i) {
        return new BlockPos(MazeData.SPAWN_X - 8 + i * 3, Y + 1, MazeData.SPAWN_Z + 6);
    }

    /** What each roster sign currently says, so unchanged boards are left alone. */
    private static final String[] ROSTER_DRAWN = new String[MazeJobs.ALL.size()];

    /**
     * Who has taken what, written on the board rather than kept in a command.
     *
     * <p>The trade board told you the four jobs existed and nothing whatsoever
     * about which of them anybody was doing. Working out whether the Glade
     * already had a Med-jack meant asking out loud and hoping somebody was
     * listening - for a decision the whole supply system now hangs on, since a
     * trade with nobody in it is a quota nobody fills and a crate that does not
     * come up.
     *
     * <p>Redrawn only when it changes. A sign block entity rewritten every second
     * is a packet to every client every second, for a board that changes about
     * four times a game.
     */
    public static void refreshRoster(ServerLevel level) {
        if (level.getServer() == null) {
            return;
        }
        MazeJobs jobs = MazeJobs.get(level.getServer());
        for (int i = 0; i < MazeJobs.ALL.size(); i++) {
            String job = MazeJobs.ALL.get(i);
            java.util.List<String> names = new java.util.ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (jobs.is(p.getUUID(), job)) {
                    String line = p.getGameProfile().getName()
                            + " §8" + jobs.levelOf(p.getUUID(), job);
                    // A Runner's number is their chart: how much of the maze
                    // they personally hold. The board is where you look to see
                    // who to ask about the north-east.
                    if (MazeJobs.RUNNER.equals(job)) {
                        line += " §b" + MazeCharts.get(level.getServer())
                                .myPercent(p.getUUID()) + "%";
                    }
                    names.add(line);
                }
            }
            String[] lines = new String[]{"§8nobody", "", "", ""};
            if (!names.isEmpty()) {
                for (int n = 0; n < 4; n++) {
                    // Four names fit. A fifth becomes "+N more" on the last line,
                    // because a truncated list that does not say it is truncated
                    // is worse than no list.
                    if (n == 3 && names.size() > 4) {
                        lines[n] = "§8+" + (names.size() - 3) + " more";
                    } else if (n < names.size()) {
                        lines[n] = "§f" + names.get(n);
                    } else {
                        lines[n] = "";
                    }
                }
            }
            // A separator no player name or level can contain.
            String joined = String.join("\n", lines);
            if (joined.equals(ROSTER_DRAWN[i])) {
                continue;
            }
            ROSTER_DRAWN[i] = joined;
            sign(level, rosterSign(i), Direction.SOUTH, lines[0], lines[1], lines[2], lines[3]);
        }
    }

    /** Forces the board to be rewritten, whatever it currently says. */
    public static void forgetRoster() {
        java.util.Arrays.fill(ROSTER_DRAWN, null);
    }

    /** The bell that counts the last two minutes of the day. */
    private static void bellTower(ServerLevel level) {
        MazeBell.build(level);
    }

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
