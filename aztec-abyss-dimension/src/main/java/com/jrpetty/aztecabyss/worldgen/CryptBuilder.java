package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * The Crypt - a sealed Aztec burial compound, built from the ground up around
 * the boarded-window mechanic.
 *
 * <p>Where the Temple is an open field with four distant gates, the Crypt is the
 * shape the mechanic actually wants: a cross of four short arms around a central
 * junction, with eight windows clustered two-to-an-arm at the ends. Nothing is
 * more than about twenty blocks from the middle, so rotating between fronts is a
 * four-second sprint rather than a hike - which is the difference between
 * triage feeling tense and feeling hopeless.
 *
 * <p>Every window is cut into solid rock with a sealed spawn chamber behind it.
 * There is no outside to this map. The horde arrives in the dark on the far side
 * of the planks, and the only way in is through them.
 *
 * <p>Deterministic: fixed seed, so the compound is identical on every server and
 * the ritual landmarks always line up.
 */
public final class CryptBuilder {

    public static final int CENTER_X = 4000;
    public static final int CENTER_Z = 0;
    public static final int FLOOR_Y = 64;

    /** Half-width of the central junction. */
    private static final int JUNCTION = 6;
    /** How far each arm reaches from the centre, interior. */
    private static final int ARM = 18;
    /** Half-width of an arm. */
    private static final int ARM_HALF = 6;
    /** Interior head height. */
    private static final int HEIGHT = 4;

    /** Depth of the spawn chamber behind each window. Matches the barricade pen. */
    private static final int PEN_DEPTH = 6;

    private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState WALL_ALT = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState TRIM = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

    private CryptBuilder() {
    }

    // ------------------------------------------------------------------
    // Landmarks shared with ArenaMap, the round manager and the ritual
    // ------------------------------------------------------------------

    /** Where hunters arrive: the mouth of the south arm. */
    public static final BlockPos ARRIVAL = new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z + 15);

    /** The extraction glyph, dead centre of the junction. */
    public static final BlockPos EXTRACTION = new BlockPos(CENTER_X, FLOOR_Y, CENTER_Z);

    /** The standings slab, set into the antechamber wall beside the arrival pad. */
    public static final BlockPos MONUMENT = new BlockPos(CENTER_X - 5, FLOOR_Y, CENTER_Z + 10);

    /**
     * The eight windows, two at the end of each arm. Order is north pair, south
     * pair, east pair, west pair - and {@link #GATE_FACINGS} runs in lockstep.
     */
    public static final BlockPos[] GATES = new BlockPos[]{
            new BlockPos(CENTER_X - 3, FLOOR_Y + 1, CENTER_Z - ARM - 1),
            new BlockPos(CENTER_X + 3, FLOOR_Y + 1, CENTER_Z - ARM - 1),
            new BlockPos(CENTER_X - 3, FLOOR_Y + 1, CENTER_Z + ARM + 1),
            new BlockPos(CENTER_X + 3, FLOOR_Y + 1, CENTER_Z + ARM + 1),
            new BlockPos(CENTER_X + ARM + 1, FLOOR_Y + 1, CENTER_Z - 3),
            new BlockPos(CENTER_X + ARM + 1, FLOOR_Y + 1, CENTER_Z + 3),
            new BlockPos(CENTER_X - ARM - 1, FLOOR_Y + 1, CENTER_Z - 3),
            new BlockPos(CENTER_X - ARM - 1, FLOOR_Y + 1, CENTER_Z + 3),
    };

    /** Which way each window faces out of the compound. */
    public static final Direction[] GATE_FACINGS = new Direction[]{
            Direction.NORTH, Direction.NORTH,
            Direction.SOUTH, Direction.SOUTH,
            Direction.EAST, Direction.EAST,
            Direction.WEST, Direction.WEST,
    };

    /** Short labels for the HUD and the callouts, in {@link #GATES} order. */
    public static final String[] GATE_LABELS = {"N1", "N2", "S1", "S2", "E1", "E2", "W1", "W2"};

    /** The four grave-candles of the hidden ritual, in the order they must be doused. */
    public static final BlockPos[] SEALS = new BlockPos[]{
            new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z - ARM + 2),      // north arm - the Vault
            new BlockPos(CENTER_X + ARM - 2, FLOOR_Y + 1, CENTER_Z),      // east arm - the Ossuary
            new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z + ARM - 2),      // south arm - the Antechamber
            new BlockPos(CENTER_X - ARM + 2, FLOOR_Y + 1, CENTER_Z),      // west arm - the Hall
    };

    /** The slab the ritual dissolves, and the cache behind it. */
    public static final BlockPos VAULT_SEAL = new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z - ARM);
    public static final BlockPos VAULT_CHEST = new BlockPos(CENTER_X, FLOOR_Y + 1, CENTER_Z - ARM - 3);

    /** Everything the wave logic sweeps: the compound plus every spawn chamber. */
    public static AABB bounds() {
        int r = ARM + PEN_DEPTH + 4;
        return new AABB(CENTER_X - r, FLOOR_Y - 6, CENTER_Z - r,
                CENTER_X + r, FLOOR_Y + 16, CENTER_Z + r);
    }

    // ------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------

    public static void build(ServerLevel level) {
        RandomSource rng = RandomSource.create(0x0CADAFEL);
        carveShell(level, rng);
        cutWindows(level);
        buildPens(level);
        dressJunction(level);
        dressVault(level);
        dressOssuary(level, rng);
        dressHall(level);
        dressAntechamber(level);
        placeSeals(level);
        placeLoot(level);
        // Sentinel under the extraction glyph: marks the Crypt as built so the
        // generator never lays it twice.
        level.setBlock(EXTRACTION.below(), TRIM, 2);
    }

    /** True for any cell inside the cross, at the given inset from the walls. */
    private static boolean inCross(int x, int z, int half, int reach) {
        return (Math.abs(x) <= half && Math.abs(z) <= reach)
                || (Math.abs(z) <= half && Math.abs(x) <= reach);
    }

    /**
     * Lays a solid block of rock over the whole cross plus a one-block skin, then
     * hollows the interior back out. Building solid-then-carving is what makes the
     * walls, floor and ceiling come out watertight with no seams to slip through.
     */
    private static void carveShell(ServerLevel level, RandomSource rng) {
        for (int x = -ARM - 2; x <= ARM + 2; x++) {
            for (int z = -ARM - 2; z <= ARM + 2; z++) {
                if (!inCross(x, z, JUNCTION + 1, ARM + 1)) {
                    continue;
                }
                for (int y = -1; y <= HEIGHT + 1; y++) {
                    BlockState s = y == -1 ? FLOOR : (rng.nextInt(6) == 0 ? WALL_ALT : WALL);
                    level.setBlock(at(x, y, z), s, 2);
                }
            }
        }
        // Hollow it out.
        for (int x = -ARM; x <= ARM; x++) {
            for (int z = -ARM; z <= ARM; z++) {
                if (!inCross(x, z, JUNCTION, ARM)) {
                    continue;
                }
                for (int y = 0; y <= HEIGHT; y++) {
                    level.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Cuts the eight windows. Each is three wide and four tall with a one-block
     * sill left at floor level - so they read as windows to climb through rather
     * than doorways to walk through, and the boards have something to sit in.
     */
    private static void cutWindows(ServerLevel level) {
        for (int i = 0; i < GATES.length; i++) {
            BlockPos g = GATES[i];
            boolean spansX = GATE_FACINGS[i].getAxis() == Direction.Axis.Z;
            for (int off = -1; off <= 1; off++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = spansX
                            ? new BlockPos(g.getX() + off, g.getY() + dy, g.getZ())
                            : new BlockPos(g.getX(), g.getY() + dy, g.getZ() + off);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // A gilded lintel and sill so each window reads as deliberate stonework.
            for (int off = -2; off <= 2; off++) {
                BlockPos lintel = spansX
                        ? new BlockPos(g.getX() + off, g.getY() + 4, g.getZ())
                        : new BlockPos(g.getX(), g.getY() + 4, g.getZ() + off);
                level.setBlock(lintel, TRIM, 2);
            }
        }
    }

    /**
     * The sealed chamber behind each window that the horde spawns into. Carved
     * out of fresh rock rather than left open, so there is genuinely no way round
     * a boarded window - not by flanking, not by climbing, not by flying.
     */
    private static void buildPens(ServerLevel level) {
        for (int i = 0; i < GATES.length; i++) {
            BlockPos g = GATES[i];
            Direction out = GATE_FACINGS[i];
            boolean spansX = out.getAxis() == Direction.Axis.Z;

            // Solid first, from one block out to the back of the chamber.
            for (int d = 1; d <= PEN_DEPTH + 1; d++) {
                BlockPos row = g.relative(out, d);
                for (int off = -3; off <= 3; off++) {
                    for (int y = -1; y <= HEIGHT + 1; y++) {
                        BlockPos p = spansX
                                ? new BlockPos(row.getX() + off, g.getY() - 1 + y, row.getZ())
                                : new BlockPos(row.getX(), g.getY() - 1 + y, row.getZ() + off);
                        level.setBlock(p, y == -1 ? Blocks.BLACKSTONE.defaultBlockState() : WALL, 2);
                    }
                }
            }
            // Then hollow the chamber.
            for (int d = 1; d <= PEN_DEPTH; d++) {
                BlockPos row = g.relative(out, d);
                for (int off = -2; off <= 2; off++) {
                    for (int y = 0; y <= HEIGHT; y++) {
                        BlockPos p = spansX
                                ? new BlockPos(row.getX() + off, g.getY() - 1 + y, row.getZ())
                                : new BlockPos(row.getX(), g.getY() - 1 + y, row.getZ() + off);
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            // Soul fire at the back: they come out of the dark, underlit.
            BlockPos back = g.relative(out, PEN_DEPTH - 1);
            level.setBlock(back.below(), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            level.setBlock(back, Blocks.SOUL_FIRE.defaultBlockState(), 2);
        }
    }

    /** The junction: four great pillars, a sunken glyph floor, hanging lanterns. */
    private static void dressJunction(ServerLevel level) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = sx * 4;
                int pz = sz * 4;
                for (int y = 0; y <= HEIGHT; y++) {
                    level.setBlock(at(px, y, pz), y == HEIGHT ? TRIM : PILLAR, 2);
                }
                level.setBlock(at(px, HEIGHT - 1, pz), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
                // A lantern slung off the inner face of each pillar.
                level.setBlock(at(px - sx, HEIGHT, pz), Blocks.SOUL_LANTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, true), 2);
            }
        }
        // Sunken glyph ring around the extraction plate.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                level.setBlock(at(x, -1, z), ring ? TRIM : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
            }
        }
    }

    /** North arm: the Vault. Sealed alcove at the far end, opened only by the ritual. */
    private static void dressVault(ServerLevel level) {
        // The sealed slab, and the cache chamber behind it.
        for (int off = -1; off <= 1; off++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(new BlockPos(VAULT_SEAL.getX() + off, VAULT_SEAL.getY() + dy, VAULT_SEAL.getZ()),
                        Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        level.setBlock(VAULT_SEAL, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(VAULT_CHEST, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(VAULT_CHEST.above(), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);

        // Gold banding along the arm so it reads as the important direction.
        for (int z = -ARM + 1; z <= -JUNCTION; z++) {
            level.setBlock(at(-ARM_HALF, HEIGHT, z), TRIM, 2);
            level.setBlock(at(ARM_HALF, HEIGHT, z), TRIM, 2);
        }
    }

    /** East arm: the Ossuary. Bone-packed walls and staring skulls. */
    private static void dressOssuary(ServerLevel level, RandomSource rng) {
        for (int x = JUNCTION + 1; x <= ARM - 1; x++) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int z = sz * ARM_HALF;
                for (int y = 0; y <= HEIGHT; y++) {
                    if (rng.nextInt(3) == 0) {
                        level.setBlock(at(x, y, z), Blocks.BONE_BLOCK.defaultBlockState()
                                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
                    }
                }
                if (rng.nextInt(4) == 0) {
                    level.setBlock(at(x, 1, z - sz), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
                }
            }
            if (rng.nextInt(5) == 0) {
                level.setBlock(at(x, HEIGHT, 0), Blocks.COBWEB.defaultBlockState(), 2);
            }
        }
    }

    /** West arm: the Hall. A colonnade of squat guardian plinths. */
    private static void dressHall(ServerLevel level) {
        for (int x = -ARM + 2; x <= -JUNCTION - 1; x += 4) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int z = sz * 3;
                level.setBlock(at(x, 0, z), PILLAR, 2);
                level.setBlock(at(x, 1, z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
                level.setBlock(at(x, 2, z), Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), 2);
            }
        }
    }

    /** South arm: the Antechamber. The arrival pad and the standings slab. */
    private static void dressAntechamber(ServerLevel level) {
        for (int x = -2; x <= 2; x++) {
            for (int z = ARM - 5; z <= ARM - 1; z++) {
                boolean edge = Math.abs(x) == 2 || z == ARM - 5 || z == ARM - 1;
                level.setBlock(at(x, -1, z), edge ? TRIM : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            level.setBlock(at(sx * 3, HEIGHT, ARM - 3), Blocks.SOUL_LANTERN.defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true), 2);
        }
    }

    /** The four grave-candles of the hidden ritual, one per arm. */
    private static void placeSeals(ServerLevel level) {
        for (BlockPos seal : SEALS) {
            level.setBlock(seal.below(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
            level.setBlock(seal, Blocks.LANTERN.defaultBlockState(), 2);
        }
    }

    /** Ordinary lootable chests, one tucked into each arm. */
    private static void placeLoot(ServerLevel level) {
        BlockPos[] spots = {
                at(-ARM_HALF + 1, 0, -ARM + 3),
                at(ARM - 3, 0, ARM_HALF - 1),
                at(ARM_HALF - 1, 0, ARM - 3),
                at(-ARM + 3, 0, -ARM_HALF + 1),
        };
        for (BlockPos p : spots) {
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 2);
        }
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(CENTER_X + x, FLOOR_Y + y, CENTER_Z + z);
    }
}
