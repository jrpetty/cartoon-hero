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

    /** Which area each window belongs to: 0 hall, 1 back room, 2 upstairs. */
    public static final int AREA_HALL = 0;
    public static final int AREA_BACK = 1;
    public static final int AREA_UPSTAIRS = 2;

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
    };

    public static final Direction[] GATE_FACINGS = new Direction[]{
            Direction.WEST, Direction.WEST, Direction.NORTH, Direction.SOUTH,
            Direction.SOUTH, Direction.EAST,
            Direction.NORTH, Direction.NORTH, Direction.EAST, Direction.SOUTH,
    };

    /** Which area each window opens into - a sealed area never spawns anything. */
    public static final int[] GATE_AREAS = new int[]{
            AREA_HALL, AREA_HALL, AREA_HALL, AREA_HALL,
            AREA_BACK, AREA_BACK,
            AREA_UPSTAIRS, AREA_UPSTAIRS, AREA_UPSTAIRS, AREA_UPSTAIRS,
    };

    public static final String[] GATE_LABELS = {
            "HALL-W1", "HALL-W2", "HALL-N", "HALL-S",
            "BACK-S", "BACK-E",
            "UP-N1", "UP-N2", "UP-E", "UP-S",
    };

    /** The two rubble piles, indexed by the area each one opens. */
    public static final BlockPos DEBRIS_BACK = new BlockPos(CENTER_X + DIVIDE_X, FLOOR_Y + 1, CENTER_Z);
    public static final BlockPos DEBRIS_STAIRS = new BlockPos(CENTER_X + SHAFT_X, FLOOR_Y + 1, CENTER_Z + 8);

    /** Four grave-candles, in the order they must be doused. Three sit behind rubble. */
    public static final BlockPos[] SEALS = new BlockPos[]{
            new BlockPos(CENTER_X - 11, FLOOR_Y + 1, CENTER_Z - 8),                 // hall
            new BlockPos(CENTER_X + 11, FLOOR_Y + 1, CENTER_Z - 8),                 // back room
            new BlockPos(CENTER_X - 1, FLOOR_Y + UPPER + 1, CENTER_Z + 6),          // upstairs
            new BlockPos(CENTER_X + 1, FLOOR_Y + UPPER + 1, CENTER_Z - 8),          // upstairs
    };

    public static final BlockPos VAULT_SEAL = new BlockPos(CENTER_X + 6, FLOOR_Y + UPPER + 1, CENTER_Z + 8);
    public static final BlockPos VAULT_CHEST = new BlockPos(CENTER_X + 6, FLOOR_Y + UPPER + 1, CENTER_Z + 7);

    public static AABB bounds() {
        int r = IN_X + PEN_DEPTH + 4;
        return new AABB(CENTER_X - r, FLOOR_Y - 6, CENTER_Z - r,
                CENTER_X + r, FLOOR_Y + ROOF + 6, CENTER_Z + r);
    }

    // ------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------

    public static void build(ServerLevel level) {
        RandomSource rng = RandomSource.create(0x4E4143485431L);
        shell(level, rng);
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
        // Sentinel under the extraction glyph: marks the Outpost as built.
        level.setBlock(EXTRACTION.below(), Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2);
    }

    /** Solid block of masonry, then the interior hollowed back out of it. */
    private static void shell(ServerLevel level, RandomSource rng) {
        for (int x = -IN_X - 1; x <= IN_X + 1; x++) {
            for (int z = -IN_Z - 1; z <= IN_Z + 1; z++) {
                for (int y = -1; y <= ROOF; y++) {
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
    }

    /** Clears a rubble pile once it has been dug out. */
    public static void clearDebris(ServerLevel level, int area) {
        if (area == AREA_BACK) {
            plug(level, DEBRIS_BACK, true, Blocks.AIR.defaultBlockState());
        } else {
            plug(level, DEBRIS_STAIRS, true, Blocks.AIR.defaultBlockState());
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
        };
        for (BlockPos p : spots) {
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 2);
        }
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
