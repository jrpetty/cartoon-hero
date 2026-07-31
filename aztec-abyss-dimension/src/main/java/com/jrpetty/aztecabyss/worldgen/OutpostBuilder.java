package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/**
 * The Outpost - a bombed-out two-storey building, modelled on the first
 * round-survival zombie map: one small derelict house, pitch dark, boarded
 * windows on every wall, and a single staircase everything funnels up.
 *
 * <p>Three areas. You start sealed in the ground-floor hall with four windows;
 * a rubble pile blocks the doorway to the back room and another blocks the foot
 * of the stairs. Clearing either opens more of the building - and more windows
 * you then have to hold. Every chest, and three of the four ritual candles, sit
 * behind that rubble, so opening up is a real trade rather than a formality:
 * the loot is in the rooms that make the map harder.
 *
 * <p>Windows carry five boards apiece rather than six, matching the barricades
 * of the era this is modelled on.
 *
 * <p>Deterministic: fixed seed, identical on every server.
 */
public final class OutpostBuilder {

    public static final int CENTER_X = 4000;
    public static final int CENTER_Z = 0;
    public static final int FLOOR_Y = 64;

    /** Interior extents of the building, relative to centre. */
    private static final int IN_X = 12;
    private static final int IN_Z = 9;
    /** Ground-floor head height, and the slab level of the upper floor. */
    private static final int UPPER = 6;
    /** Roof height. */
    private static final int ROOF = 12;
    /** The dividing wall between hall and back room. */
    private static final int DIVIDE_X = 4;
    /** West wall of the stairwell shaft, which sits in the back room's far corner. */
    private static final int SHAFT_X = 8;
    /** Where the upper floor begins - west of this the hall is double height. */
    private static final int UPPER_EDGE = -2;
    /** Walkable floor of the cellar, and the block course beneath it. */
    private static final int CELLAR = -5;
    /** East edge of the cellar and of the stair well that drops into it. */
    private static final int CELLAR_EAST = 3;

    private static final int PEN_DEPTH = 6;

    private static final BlockState WALL = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_ALT = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState WALL_PLAIN = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState GROUND = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState BOARDS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState BEAM = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
    private static final BlockState RUBBLE = Blocks.COBBLESTONE.defaultBlockState();

    private OutpostBuilder() {
    }

    // ------------------------------------------------------------------
    // Landmarks
    // ------------------------------------------------------------------

    /** Arrival: the middle of the ground-floor hall. */
    public static final BlockPos ARRIVAL = new BlockPos(CENTER_X - 7, FLOOR_Y + 1, CENTER_Z + 2);

    /** Extraction glyph, in the hall so it is always reachable. */
    public static final BlockPos EXTRACTION = new BlockPos(CENTER_X - 9, FLOOR_Y, CENTER_Z - 6);

    /** The standings slab on the hall's west wall. */
    public static final BlockPos MONUMENT = new BlockPos(CENTER_X - 12, FLOOR_Y, CENTER_Z - 2);

    /** Which area each window belongs to. */
    public static final int AREA_HALL = 0;
    public static final int AREA_BACK = 1;
    public static final int AREA_UPSTAIRS = 2;
    public static final int AREA_CELLAR = 3;

    /**
     * The ten windows. Ground-floor windows sit at head height on the outer
     * walls; the four upstairs ones are deliberately offset from those below, so
     * no pen ever sits directly on top of another.
     */
    public static final BlockPos[] GATES = new BlockPos[]{
            new BlockPos(CENTER_X - IN_X - 1, FLOOR_Y + 1, CENTER_Z - 5),   // 0 hall, west
            new BlockPos(CENTER_X - IN_X - 1, FLOOR_Y + 1, CENTER_Z + 5),   // 1 hall, west
            new BlockPos(CENTER_X - 8, FLOOR_Y + 1, CENTER_Z - IN_Z - 1),   // 2 hall, north
            new BlockPos(CENTER_X - 8, FLOOR_Y + 1, CENTER_Z + IN_Z + 1),   // 3 hall, south
            new BlockPos(CENTER_X + 6, FLOOR_Y + 1, CENTER_Z + IN_Z + 1),   // 4 back room, south
            new BlockPos(CENTER_X + IN_X + 1, FLOOR_Y + 1, CENTER_Z - 5),   // 5 back room, east
            new BlockPos(CENTER_X + 2, FLOOR_Y + UPPER + 1, CENTER_Z - IN_Z - 1),  // 6 upstairs, north
            new BlockPos(CENTER_X + 9, FLOOR_Y + UPPER + 1, CENTER_Z - IN_Z - 1),  // 7 upstairs, north
            new BlockPos(CENTER_X + IN_X + 1, FLOOR_Y + UPPER + 1, CENTER_Z - 4),  // 8 upstairs, east
            new BlockPos(CENTER_X + 3, FLOOR_Y + UPPER + 1, CENTER_Z + IN_Z + 1),  // 9 upstairs, south
            // Coal chutes at cellar floor level - no sill, so these come at you fast.
            new BlockPos(CENTER_X - 10, FLOOR_Y + CELLAR, CENTER_Z + IN_Z + 1),    // 10 cellar, south
            new BlockPos(CENTER_X - 3, FLOOR_Y + CELLAR, CENTER_Z + IN_Z + 1),     // 11 cellar, south
    };

    public static final Direction[] GATE_FACINGS = new Direction[]{
            Direction.WEST, Direction.WEST, Direction.NORTH, Direction.SOUTH,
            Direction.SOUTH, Direction.EAST,
            Direction.NORTH, Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.SOUTH, Direction.SOUTH,
    };

    /** Which area each window opens into - a sealed area never spawns anything. */
    public static final int[] GATE_AREAS = new int[]{
            AREA_HALL, AREA_HALL, AREA_HALL, AREA_HALL,
            AREA_BACK, AREA_BACK,
            AREA_UPSTAIRS, AREA_UPSTAIRS, AREA_UPSTAIRS, AREA_UPSTAIRS,
            AREA_CELLAR, AREA_CELLAR,
    };

    public static final String[] GATE_LABELS = {
            "HALL-W1", "HALL-W2", "HALL-N", "HALL-S",
            "BACK-S", "BACK-E",
            "UP-N1", "UP-N2", "UP-E", "UP-S",
            "CELLAR-W", "CELLAR-E",
    };

    /** The two rubble piles, indexed by the area each one opens. */
    public static final BlockPos DEBRIS_BACK = new BlockPos(CENTER_X + DIVIDE_X, FLOOR_Y, CENTER_Z);
    public static final BlockPos DEBRIS_STAIRS = new BlockPos(CENTER_X + SHAFT_X, FLOOR_Y, CENTER_Z + 8);
    public static final BlockPos DEBRIS_CELLAR = new BlockPos(CENTER_X + 2, FLOOR_Y, CENTER_Z - 3);

    /** Four grave-candles, in the order they must be doused. Three sit behind rubble. */
    public static final BlockPos[] SEALS = new BlockPos[]{
            new BlockPos(CENTER_X - 11, FLOOR_Y + 1, CENTER_Z - 8),                 // hall
            new BlockPos(CENTER_X + 11, FLOOR_Y + 1, CENTER_Z - 8),                 // back room
            new BlockPos(CENTER_X - 6, FLOOR_Y + CELLAR, CENTER_Z + 4),             // cellar
            new BlockPos(CENTER_X + 1, FLOOR_Y + UPPER + 1, CENTER_Z - 8),          // upstairs
    };

    /**
     * Where each wall buy is bolted, in {@link com.jrpetty.aztecabyss.round.OutpostShop#CATALOGUE}
     * order. Spread so the cheap essentials are in the hall you start in and the
     * things that win rounds are behind rubble - the shop is another reason to
     * open the building up, and another reason not to.
     */
    public static final BlockPos[] SHOP = new BlockPos[]{
            new BlockPos(CENTER_X - IN_X, FLOOR_Y + 1, CENTER_Z + 2),          // stone sword - hall
            new BlockPos(CENTER_X + IN_X, FLOOR_Y + 1, CENTER_Z - 8),          // iron sword - back
            new BlockPos(CENTER_X - IN_X, FLOOR_Y + CELLAR + 1, CENTER_Z),     // iron axe - cellar
            new BlockPos(CENTER_X - IN_X, FLOOR_Y + 1, CENTER_Z + 5),          // bow - hall
            new BlockPos(CENTER_X - IN_X, FLOOR_Y + 1, CENTER_Z + 7),          // arrows - hall
            new BlockPos(CENTER_X + IN_X, FLOOR_Y + UPPER + 1, CENTER_Z - 8),  // crossbow - upstairs
            new BlockPos(CENTER_X + 6, FLOOR_Y + 1, CENTER_Z - IN_Z),          // shield - back
            new BlockPos(CENTER_X - 1, FLOOR_Y + UPPER + 1, CENTER_Z - IN_Z),  // chestplate - upstairs
            new BlockPos(CENTER_X - 4, FLOOR_Y + 1, CENTER_Z - IN_Z),          // rations - hall
    };

    /** The Crucible, upstairs and deliberately behind two lots of rubble. */
    public static final BlockPos CRUCIBLE = new BlockPos(CENTER_X + 7, FLOOR_Y + UPPER + 1, CENTER_Z - 5);

    public static final BlockPos VAULT_SEAL = new BlockPos(CENTER_X + 6, FLOOR_Y + UPPER + 1, CENTER_Z + 8);
    public static final BlockPos VAULT_CHEST = new BlockPos(CENTER_X + 6, FLOOR_Y + UPPER + 1, CENTER_Z + 7);

    public static AABB bounds() {
        int r = IN_X + PEN_DEPTH + 4;
        return new AABB(CENTER_X - r, FLOOR_Y + CELLAR - 8, CENTER_Z - r,
                CENTER_X + r, FLOOR_Y + ROOF + 6, CENTER_Z + r);
    }

    // ------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------

    public static void build(ServerLevel level) {
        RandomSource rng = RandomSource.create(0x4E4143485431L);
        shell(level, rng);
        cellar(level, rng);
        upperFloor(level, rng);
        divideWall(level);
        staircase(level);  // must follow the floor: it cuts the stairwell out of it
        cutWindows(level);
        buildPens(level);
        dressHall(level, rng);
        dressBackRoom(level, rng);
        dressUpstairs(level, rng);
        placeDebris(level);
        placeSeals(level);
        placeLoot(level);
        shopFronts(level);
        crucible(level);
        detail(level, rng);
        // Sentinel under the extraction glyph: marks the Outpost as built.
        level.setBlock(EXTRACTION.below(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
    }

    /** Solid block of masonry, then the interior hollowed back out of it. */
    private static void shell(ServerLevel level, RandomSource rng) {
        for (int x = -IN_X - 1; x <= IN_X + 1; x++) {
            for (int z = -IN_Z - 1; z <= IN_Z + 1; z++) {
                for (int y = CELLAR - 2; y <= ROOF; y++) {
                    level.setBlock(at(x, y, z), wallStone(rng), 2);
                }
            }
        }
        for (int x = -IN_X; x <= IN_X; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                level.setBlock(at(x, -1, z), GROUND, 2);
                for (int y = 0; y < ROOF; y++) {
                    level.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * The cellar under the hall, and the stair well that drops into it.
     *
     * <p>Reached through a boarded-up hatch in the hall's north-east corner. Its
     * two windows are coal chutes at floor level with no sill at all, so nothing
     * has to climb to get in - which is what makes the cheapest loot on the map
     * sit down here.
     */
    private static void cellar(ServerLevel level, RandomSource rng) {
        // Hollow the cellar out under the hall.
        for (int x = -IN_X; x <= CELLAR_EAST; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                level.setBlock(at(x, CELLAR - 1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
                for (int y = CELLAR; y <= -2; y++) {
                    level.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // Drop the hall floor away over the stair well.
        for (int x = 0; x <= CELLAR_EAST; x++) {
            for (int z = -IN_Z; z <= -4; z++) {
                level.setBlock(at(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Five steps down, running north into the cellar's far corner.
        for (int i = 0; i <= 4; i++) {
            int z = -4 - i;
            for (int x = 0; x <= CELLAR_EAST; x++) {
                for (int y = CELLAR; y <= -1 - i; y++) {
                    level.setBlock(at(x, y, z), y == -1 - i
                            ? Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Kerb round the opening so nobody walks into it in the dark, with the
        // one hatch through it that the rubble seals.
        for (int x = -1; x <= CELLAR_EAST + 1; x++) {
            for (int y = 0; y < UPPER; y++) {
                boolean hatch = x >= 1 && x <= CELLAR_EAST && y <= 2;
                level.setBlock(at(x, y, -3), hatch ? Blocks.AIR.defaultBlockState() : WALL, 2);
            }
        }
        for (int z = -IN_Z; z <= -3; z++) {
            for (int y = 0; y < UPPER; y++) {
                level.setBlock(at(-1, y, z), WALL, 2);
            }
        }
        // Iron bars and damp: it reads as a cellar, not a second hall.
        for (int z = -IN_Z + 1; z <= IN_Z - 1; z += 4) {
            level.setBlock(at(-IN_X + 1, CELLAR + 2, z), Blocks.IRON_BARS.defaultBlockState(), 2);
            if (rng.nextBoolean()) {
                level.setBlock(at(-IN_X + 2, CELLAR, z), Blocks.COBWEB.defaultBlockState(), 2);
            }
        }
        level.setBlock(at(-6, CELLAR + 3, 0), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
    }

    private static BlockState wallStone(RandomSource rng) {
        int r = rng.nextInt(10);
        return r < 4 ? WALL : r < 7 ? WALL_ALT : WALL_PLAIN;
    }

    /**
     * The upper floor, laid over the eastern two-thirds only. The western end of
     * the hall stays double height, so from the landing you look down over the
     * room you started in - and anything that gets in downstairs is visible from
     * up there long before it finds the stairs.
     */
    private static void upperFloor(ServerLevel level, RandomSource rng) {
        for (int x = UPPER_EDGE; x <= IN_X; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                level.setBlock(at(x, UPPER, z), rng.nextInt(9) == 0
                        ? Blocks.AIR.defaultBlockState() : BOARDS, 2);
            }
        }
        // Exposed joists under the boards, and a rail along the open edge.
        for (int z = -IN_Z; z <= IN_Z; z += 3) {
            for (int x = UPPER_EDGE; x <= IN_X; x++) {
                level.setBlock(at(x, UPPER - 1, z), BEAM, 2);
            }
        }
        for (int z = -IN_Z; z <= IN_Z; z++) {
            level.setBlock(at(UPPER_EDGE, UPPER + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
        }
    }

    /** The wall between hall and back room, with the rubble-choked doorway. */
    private static void divideWall(ServerLevel level) {
        for (int z = -IN_Z; z <= IN_Z; z++) {
            for (int y = 0; y < UPPER; y++) {
                boolean doorway = z >= -1 && z <= 1 && y <= 2;
                level.setBlock(at(DIVIDE_X, y, z),
                        doorway ? Blocks.AIR.defaultBlockState() : WALL, 2);
            }
        }
    }

    /**
     * The one staircase, boxed into its own shaft in the back corner.
     *
     * <p>It has to be a sealed shaft with a single doorway, not an open flight of
     * steps - a rubble pile in an open room is scenery you walk around, and the
     * whole point is that it holds you (and the horde) out until you dig it.
     */
    private static void staircase(ServerLevel level) {
        // Shaft walls: west face along SHAFT_X, north face along z = 1.
        for (int z = 1; z <= IN_Z; z++) {
            for (int y = 0; y < UPPER; y++) {
                boolean doorway = z >= IN_Z - 2 && y <= 2;
                level.setBlock(at(SHAFT_X, y, z), doorway ? Blocks.AIR.defaultBlockState() : WALL, 2);
            }
        }
        for (int x = SHAFT_X; x <= IN_X; x++) {
            for (int y = 0; y < UPPER; y++) {
                level.setBlock(at(x, y, 1), WALL, 2);
            }
        }
        // Open the floor above the shaft so the flight actually emerges.
        for (int x = SHAFT_X + 1; x <= IN_X; x++) {
            for (int z = 3; z <= IN_Z; z++) {
                level.setBlock(at(x, UPPER, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Six steps climbing north, landing on the boards at z = 2.
        for (int i = 0; i < UPPER; i++) {
            int z = IN_Z - 1 - i;
            for (int x = SHAFT_X + 2; x <= IN_X; x++) {
                for (int y = 0; y <= i; y++) {
                    level.setBlock(at(x, y, z), y == i
                            ? Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                            : WALL_PLAIN, 2);
                }
            }
        }
    }

    /**
     * Cuts the ten windows: three wide, four tall, a sill left at floor level so
     * they read as something to be climbed through.
     */
    private static void cutWindows(ServerLevel level) {
        for (int i = 0; i < GATES.length; i++) {
            BlockPos g = GATES[i];
            boolean spansX = GATE_FACINGS[i].getAxis() == Direction.Axis.Z;
            for (int off = -1; off <= 1; off++) {
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(cell(g, spansX, off, dy), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // Timber lintel over each opening.
            for (int off = -2; off <= 2; off++) {
                level.setBlock(cell(g, spansX, off, 4), BEAM, 2);
            }
        }
    }

    /** A sealed chamber behind every window. There is no outside to this map. */
    /**
     * A sealed chamber behind every window. There is no outside to this map.
     *
     * <p>Every chamber is filled solid before any of them is hollowed out. Doing
     * it per-window would let a later pen's stonework backfill an earlier one -
     * which matters here, because the cellar chutes sit almost directly beneath
     * the hall's south windows.
     */
    private static void buildPens(ServerLevel level) {
        for (int i = 0; i < GATES.length; i++) {
            BlockPos g = GATES[i];
            Direction out = GATE_FACINGS[i];
            boolean spansX = out.getAxis() == Direction.Axis.Z;
            for (int d = 1; d <= PEN_DEPTH + 1; d++) {
                BlockPos row = g.relative(out, d);
                for (int off = -3; off <= 3; off++) {
                    for (int y = -2; y <= 5; y++) {
                        level.setBlock(cell(row, spansX, off, y), WALL_PLAIN, 2);
                    }
                }
            }
        }
        for (int i = 0; i < GATES.length; i++) {
            BlockPos g = GATES[i];
            Direction out = GATE_FACINGS[i];
            boolean spansX = out.getAxis() == Direction.Axis.Z;
            for (int d = 1; d <= PEN_DEPTH; d++) {
                BlockPos row = g.relative(out, d);
                for (int off = -2; off <= 2; off++) {
                    for (int y = -1; y <= 3; y++) {
                        level.setBlock(cell(row, spansX, off, y), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            BlockPos back = g.relative(out, PEN_DEPTH - 1);
            level.setBlock(cell(back, spansX, 0, -2), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            level.setBlock(cell(back, spansX, 0, -1), Blocks.SOUL_FIRE.defaultBlockState(), 2);
        }
    }

    /** The hall: your first four windows, the arrival pad, rubble underfoot. */
    private static void dressHall(ServerLevel level, RandomSource rng) {
        for (int x = -IN_X; x <= UPPER_EDGE; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                if (rng.nextInt(7) == 0) {
                    level.setBlock(at(x, 0, z), rng.nextBoolean()
                            ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                            : Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
                }
                if (rng.nextInt(24) == 0) {
                    level.setBlock(at(x, ROOF - 1, z), Blocks.COBWEB.defaultBlockState(), 2);
                }
            }
        }
        // The one lit corner. Everything else you find by muzzle flash.
        level.setBlock(at(-IN_X + 1, 3, -IN_Z + 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        // Extraction plate, ringed in gold so it reads in the dark.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(EXTRACTION.offset(x, -1, z),
                        Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
            }
        }
    }

    /** The back room, and the word every player of the original remembers. */
    private static void dressBackRoom(ServerLevel level, RandomSource rng) {
        for (int x = DIVIDE_X + 1; x <= IN_X; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                if (rng.nextInt(9) == 0) {
                    level.setBlock(at(x, 0, z), Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2);
                }
            }
        }
        BlockPos sign = at(DIVIDE_X + 1, 2, -IN_Z);
        level.setBlock(sign, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        if (level.getBlockEntity(sign) instanceof SignBlockEntity be) {
            be.updateText(t -> t.setMessage(1, Component.literal("§4§lHELP")), true);
        }
        level.setBlock(at(IN_X - 1, 3, -IN_Z + 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
    }

    /** The landing: four windows, the deepest loot, and a view down into the hall. */
    private static void dressUpstairs(ServerLevel level, RandomSource rng) {
        for (int x = UPPER_EDGE + 1; x <= IN_X; x++) {
            for (int z = -IN_Z; z <= IN_Z; z++) {
                if (rng.nextInt(16) == 0) {
                    level.setBlock(at(x, UPPER + 1, z), Blocks.COBWEB.defaultBlockState(), 2);
                }
            }
        }
        // The floorboards are laid with random gaps; make sure none of them
        // landed under something that has to stand on solid ground.
        for (BlockPos anchor : new BlockPos[]{VAULT_SEAL, VAULT_CHEST, SEALS[2], SEALS[3]}) {
            level.setBlock(anchor.below(), BOARDS, 2);
        }
        // The sealed cache the ritual opens.
        level.setBlock(VAULT_SEAL, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(VAULT_CHEST, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(at(UPPER_EDGE + 2, UPPER + 4, 0), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
    }

    /** The two rubble piles that seal the back room and the stairs. */
    public static void placeDebris(ServerLevel level) {
        plug(level, DEBRIS_BACK, true, RUBBLE);
        plug(level, DEBRIS_STAIRS, true, RUBBLE);
        plug(level, DEBRIS_CELLAR, false, RUBBLE);
    }

    /** Clears a rubble pile once it has been dug out. */
    public static void clearDebris(ServerLevel level, int area) {
        switch (area) {
            case AREA_BACK -> plug(level, DEBRIS_BACK, true, Blocks.AIR.defaultBlockState());
            case AREA_UPSTAIRS -> plug(level, DEBRIS_STAIRS, true, Blocks.AIR.defaultBlockState());
            case AREA_CELLAR -> plug(level, DEBRIS_CELLAR, false, Blocks.AIR.defaultBlockState());
            default -> {
            }
        }
    }

    /** A 3-wide, 3-tall plug centred on a blocking position. */
    private static void plug(ServerLevel level, BlockPos centre, boolean spansZ, BlockState state) {
        for (int off = -1; off <= 1; off++) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos p = spansZ
                        ? new BlockPos(centre.getX(), centre.getY() + dy, centre.getZ() + off)
                        : new BlockPos(centre.getX() + off, centre.getY() + dy, centre.getZ());
                level.setBlock(p, state, 2);
            }
        }
    }

    /** Which rubble pile a clicked block belongs to, or -1. */
    public static int debrisAreaNear(BlockPos pos) {
        if (within(pos, DEBRIS_BACK)) {
            return AREA_BACK;
        }
        if (within(pos, DEBRIS_STAIRS)) {
            return AREA_UPSTAIRS;
        }
        if (within(pos, DEBRIS_CELLAR)) {
            return AREA_CELLAR;
        }
        return -1;
    }

    private static boolean within(BlockPos pos, BlockPos centre) {
        return Math.abs(pos.getX() - centre.getX()) <= 1
                && Math.abs(pos.getZ() - centre.getZ()) <= 1
                && pos.getY() >= centre.getY() && pos.getY() <= centre.getY() + 2;
    }

    private static void placeSeals(ServerLevel level) {
        for (BlockPos seal : SEALS) {
            level.setBlock(seal.below(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(seal, Blocks.LANTERN.defaultBlockState(), 2);
        }
    }

    /** Every chest sits behind rubble - the loot is in the rooms that cost you. */
    private static void placeLoot(ServerLevel level) {
        BlockPos[] spots = {
                at(DIVIDE_X + 2, 0, -IN_Z + 2),      // back room
                at(IN_X - 1, 0, -IN_Z + 2),          // back room
                at(IN_X - 1, UPPER + 1, -IN_Z + 2),  // upstairs
                at(UPPER_EDGE + 2, UPPER + 1, IN_Z - 1),
                at(-IN_X + 1, CELLAR, IN_Z - 2),     // cellar
                at(-5, CELLAR, -IN_Z + 2),           // cellar
        };
        for (BlockPos p : spots) {
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    /**
     * The dressing pass. Everything here is scenery, but scenery is most of what
     * makes a small dark box feel like somewhere people used to live and then
     * had to leave in a hurry.
     */
    private static void detail(ServerLevel level, RandomSource rng) {
        sandbags(level);
        furniture(level);
        collapsedRoof(level, rng);
        bloodAndSoot(level, rng);
        scrawls(level);
        hangings(level, rng);
    }

    /** Sandbag emplacements thrown up under the ground-floor windows. */
    private static void sandbags(ServerLevel level) {
        for (int i = 0; i < GATES.length; i++) {
            if (GATE_AREAS[i] == AREA_UPSTAIRS || GATE_AREAS[i] == AREA_CELLAR) {
                continue;
            }
            BlockPos g = GATES[i];
            Direction in = GATE_FACINGS[i].getOpposite();
            boolean spansX = GATE_FACINGS[i].getAxis() == Direction.Axis.Z;
            BlockPos inner = g.relative(in);
            for (int off = -2; off <= 2; off++) {
                if (off == 0) {
                    continue; // leave the middle clear so you can still reach the boards
                }
                level.setBlock(cell(inner, spansX, off, -1),
                        Blocks.PACKED_MUD.defaultBlockState(), 2);
                if (Math.abs(off) == 1) {
                    level.setBlock(cell(inner, spansX, off, 0),
                            Blocks.MUD_BRICK_WALL.defaultBlockState(), 2);
                }
            }
        }
    }

    /** What the last tenants left behind. */
    private static void furniture(ServerLevel level) {
        // Hall: a dead hearth, a work bench, a toppled barrel.
        level.setBlock(at(-IN_X + 1, 0, 4), Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, false), 2);
        level.setBlock(at(-IN_X + 1, 1, 4), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(at(-IN_X + 1, 0, 6), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        level.setBlock(at(-IN_X + 2, 0, 7), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(-10, 0, -8), Blocks.DAMAGED_ANVIL.defaultBlockState(), 2);
        level.setBlock(at(-4, 0, 8), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(at(-3, 0, -8), Blocks.POTTED_DEAD_BUSH.defaultBlockState(), 2);

        // Back room: a field kitchen and a shelf of ruined books.
        level.setBlock(at(DIVIDE_X + 2, 0, 3), Blocks.SMOKER.defaultBlockState(), 2);
        level.setBlock(at(DIVIDE_X + 1, 0, 5), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(DIVIDE_X + 1, 0, -4), Blocks.BOOKSHELF.defaultBlockState(), 2);
        level.setBlock(at(DIVIDE_X + 1, 1, -4), Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 2);
        level.setBlock(at(IN_X - 2, 0, 4), Blocks.FLETCHING_TABLE.defaultBlockState(), 2);

        // Upstairs: bunks and a lookout's stool at the rail.
        for (int z = -6; z <= 6; z += 4) {
            level.setBlock(at(IN_X - 1, UPPER + 1, z), Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, false)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
        }
        level.setBlock(at(UPPER_EDGE + 1, UPPER + 1, -2), Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 2);
        level.setBlock(at(UPPER_EDGE + 1, UPPER + 1, 3), Blocks.BARREL.defaultBlockState(), 2);

        // Cellar: stores, and something that was being kept down here.
        level.setBlock(at(-IN_X + 2, CELLAR, -6), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(-IN_X + 2, CELLAR + 1, -6), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(-IN_X + 3, CELLAR, -6), Blocks.DECORATED_POT.defaultBlockState(), 2);
        level.setBlock(at(-8, CELLAR, 5), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
        level.setBlock(at(-9, CELLAR, 6), Blocks.BONE_BLOCK.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 2);
    }

    /**
     * A shell hole through the roof over the double-height hall, with the rafters
     * left hanging. It is the one place the Abyss gets to look in at you.
     */
    private static void collapsedRoof(ServerLevel level, RandomSource rng) {
        for (int x = -9; x <= -5; x++) {
            for (int z = -3; z <= 2; z++) {
                if (Math.abs(x + 7) + Math.abs(z) <= 4) {
                    level.setBlock(at(x, ROOF, z), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(at(x, ROOF - 1, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // Snapped rafters left jutting over the hole.
        for (int x = -10; x <= -4; x++) {
            if (rng.nextInt(3) != 0) {
                level.setBlock(at(x, ROOF - 1, -4), BEAM, 2);
            }
            if (rng.nextInt(3) != 0) {
                level.setBlock(at(x, ROOF - 1, 3), BEAM, 2);
            }
        }
        // The pile of it on the floor below.
        for (int x = -9; x <= -5; x++) {
            for (int z = -2; z <= 1; z++) {
                if (rng.nextInt(3) == 0) {
                    level.setBlock(at(x, 0, z), Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Old stains and burn marks. Sparse - a little of this goes a long way. */
    private static void bloodAndSoot(ServerLevel level, RandomSource rng) {
        for (int n = 0; n < 26; n++) {
            int x = -IN_X + rng.nextInt(IN_X * 2);
            int z = -IN_Z + rng.nextInt(IN_Z * 2);
            int y = rng.nextInt(3) == 0 ? UPPER + 1 : 0;
            if (y == UPPER + 1 && x < UPPER_EDGE) {
                continue;
            }
            if (level.getBlockState(at(x, y, z)).isAir()) {
                level.setBlock(at(x, y, z), rng.nextInt(3) == 0
                        ? Blocks.RED_CARPET.defaultBlockState()
                        : Blocks.BROWN_CARPET.defaultBlockState(), 2);
            }
        }
    }

    /** Messages left on the walls by whoever was here before you. */
    private static void scrawls(ServerLevel level) {
        scrawl(level, at(-IN_X, 2, 0), Direction.EAST, "§4THEY COME", "§4THROUGH THE", "§4WINDOWS");
        scrawl(level, at(-IN_X, 2, 7), Direction.EAST, "§8FIVE BOARDS", "§8EVERY ONE", "§8EVERY TIME");
        scrawl(level, at(0, UPPER + 3, -IN_Z), Direction.SOUTH, "§8HIGH GROUND", "§8IS A LIE", "");
        scrawl(level, at(-IN_X + 4, CELLAR + 2, -IN_Z), Direction.SOUTH, "§4WE SEALED", "§4THE CELLAR", "§4FOR A REASON");
    }

    private static void scrawl(ServerLevel level, BlockPos pos, Direction facing, String a, String b, String c) {
        level.setBlock(pos, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            be.updateText(t -> t.setMessage(0, Component.literal(a))
                    .setMessage(1, Component.literal(b))
                    .setMessage(2, Component.literal(c)), true);
        }
    }

    /** Chains, cobwebs and guttering candles across all four floors' worth of dark. */
    private static void hangings(ServerLevel level, RandomSource rng) {
        for (int n = 0; n < 18; n++) {
            int x = -IN_X + rng.nextInt(IN_X * 2);
            int z = -IN_Z + rng.nextInt(IN_Z * 2);
            int ceil = x >= UPPER_EDGE ? UPPER + 4 : ROOF - 1;
            if (level.getBlockState(at(x, ceil, z)).isAir()) {
                level.setBlock(at(x, ceil, z), Blocks.CHAIN.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
            }
        }
        BlockPos[] candles = {
                at(-IN_X + 1, 1, -3), at(-5, 1, 7), at(DIVIDE_X + 3, 1, -6),
                at(IN_X - 3, UPPER + 1, 5), at(-2, UPPER + 1, -6), at(-7, CELLAR, -2),
        };
        for (BlockPos c : candles) {
            if (level.getBlockState(c).isAir()) {
                level.setBlock(c, Blocks.CANDLE.defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true), 2);
            }
        }
    }

    /** A lit plate and a price board at every wall buy. */
    private static void shopFronts(ServerLevel level) {
        var cat = com.jrpetty.aztecabyss.round.OutpostShop.CATALOGUE;
        for (int i = 0; i < SHOP.length && i < cat.length; i++) {
            BlockPos at = SHOP[i];
            level.setBlock(at, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(at.above(), Blocks.LANTERN.defaultBlockState(), 2);
            BlockPos board = at.below();
            Direction face = at.getX() <= CENTER_X - IN_X ? Direction.EAST
                    : at.getX() >= CENTER_X + IN_X ? Direction.WEST : Direction.SOUTH;
            level.setBlock(board, Blocks.OAK_WALL_SIGN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, face), 2);
            final int idx = i;
            if (level.getBlockEntity(board) instanceof SignBlockEntity be) {
                be.updateText(t -> t
                        .setMessage(0, Component.literal("§8— WALL BUY —"))
                        .setMessage(1, Component.literal("§f" + cat[idx].label()))
                        .setMessage(2, Component.literal("§e" + cat[idx].price() + " pts"))
                        .setMessage(3, Component.literal("§8right-click")), true);
            }
        }
    }

    /**
     * The Crucible: a squat furnace-and-anvil machine wedged into the upstairs
     * wall, lit from inside. It reads as something salvaged and rewired rather
     * than built, which is the right note for a thing that turns a wooden sword
     * into something with a name.
     */
    private static void crucible(ServerLevel level) {
        BlockPos c = CRUCIBLE;
        level.setBlock(c.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(c, Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        level.setBlock(c.above(), Blocks.ANVIL.defaultBlockState(), 2);
        level.setBlock(c.above(2), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        for (Direction d : new Direction[]{Direction.EAST, Direction.WEST}) {
            level.setBlock(c.relative(d), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 2);
            level.setBlock(c.relative(d).above(), Blocks.REDSTONE_LAMP.defaultBlockState()
                    .setValue(BlockStateProperties.LIT, true), 2);
            level.setBlock(c.relative(d).above(2), Blocks.CHAIN.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
        }
        BlockPos plate = c.relative(Direction.SOUTH);
        level.setBlock(plate.below(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
        BlockPos board = c.above(2).relative(Direction.SOUTH);
        level.setBlock(board, Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        if (level.getBlockEntity(board) instanceof SignBlockEntity be) {
            be.updateText(t -> t
                    .setMessage(0, Component.literal("§5§lTHE CRUCIBLE"))
                    .setMessage(1, Component.literal("§7one tier up"))
                    .setMessage(2, Component.literal("§7and a name"))
                    .setMessage(3, Component.literal("§8right-click")), true);
        }
    }

    /** Which wall buy a clicked block is, or -1. */
    public static int shopIndexNear(BlockPos pos) {
        for (int i = 0; i < SHOP.length; i++) {
            BlockPos s = SHOP[i];
            if (Math.abs(pos.getX() - s.getX()) <= 1 && Math.abs(pos.getZ() - s.getZ()) <= 1
                    && Math.abs(pos.getY() - s.getY()) <= 1) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a clicked block is the Crucible. */
    public static boolean isCrucible(BlockPos pos) {
        return Math.abs(pos.getX() - CRUCIBLE.getX()) <= 1
                && Math.abs(pos.getZ() - CRUCIBLE.getZ()) <= 1
                && Math.abs(pos.getY() - CRUCIBLE.getY()) <= 2;
    }

    private static BlockPos cell(BlockPos origin, boolean spansX, int off, int dy) {
        return spansX
                ? new BlockPos(origin.getX() + off, origin.getY() + dy, origin.getZ())
                : new BlockPos(origin.getX(), origin.getY() + dy, origin.getZ() + off);
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(CENTER_X + x, FLOOR_Y + y, CENTER_Z + z);
    }
}
