package com.jrpetty.aztecabyss.round;

import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/**
 * The boarded horde gates - the Abyss' answer to the barricaded windows of
 * round-survival zombie modes.
 *
 * Each gate on a barricade map wears six boards. The horde spawns penned in a
 * sealed gatehouse behind the arch and cannot reach the arena until it has
 * ripped those boards off one at a time; hunters can nail them back on. The
 * boards are not a wall and are never meant to be one - they are a throttle on
 * how fast the arena fills, and a standing question of which two of four gates
 * you can actually afford to hold.
 *
 * <p>Boards deliberately do <em>not</em> mend themselves between rounds. The
 * breather is the repair window, which is what gives it something to be for.
 *
 * <p>State lives here as flat arrays indexed by gate, so a tick costs four small
 * box queries and nothing else; blocks are only ever written when a board
 * actually changes.
 */
public final class Barricade {

    /** Boards on a fully sound gate. Matches the six-plank barricades of the genre. */
    public static final int MAX_BOARDS = 6;

    /** How far the sealed gatehouse extends out behind the arch. */
    public static final int POCKET_DEPTH = 6;
    /** Half-width of the gatehouse, measured from the gate's centre line. */
    public static final int POCKET_HALF_WIDTH = 3;
    /** How far back in the pen the horde materialises - far enough to have to walk up. */
    private static final int SPAWN_DEPTH = 4;

    /**
     * Where each board sits in the gate mouth, as two {off, dy} cells apiece.
     *
     * <p>The gate opening is three wide and four tall. Boards are laid so the set
     * reads as four staggered planks pinned by two upright braces rather than a
     * tidy grid - and because boards are stripped highest-index-first, the braces
     * are torn away before the planks, and the bottom plank is the last thing
     * standing. Watching a gate come apart tells you how long it has left.
     */
    private static final int[][] BOARD_CELLS = {
            {-1, 0, 0, 0},    // 0 - bottom plank: last to fall, first to go back up
            {0, 1, 1, 1},     // 1
            {-1, 2, 0, 2},    // 2
            {0, 3, 1, 3},     // 3
            {1, 0, 1, 2},     // 4 - right-hand upright brace
            {-1, 1, -1, 3},   // 5 - left-hand upright brace: first to be ripped away
    };

    /** Mismatched salvage rather than a matching set - these were nailed up in a hurry. */
    private static final Block[] BOARD_WOOD = {
            Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR,
            Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR,
    };

    /** Boards remaining per gate. */
    private static int[] boards = new int[0];
    /** Accumulated tearing effort per gate, in plain-mob-seconds toward the next board. */
    private static float[] effort = new float[0];

    private Barricade() {
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    /**
     * The direction pointing out of the arena through a gate.
     *
     * <p>Read from the map rather than derived from the gate's coordinates. The
     * old derivation only worked for arenas whose gates sat on the cardinal axes
     * through the world origin - fine for the Temple, meaningless the moment a
     * map puts two windows side by side in the same wall.
     */
    public static Direction outward(ArenaMap map, int gate) {
        Direction[] facings = map.gateFacings();
        return gate >= 0 && gate < facings.length ? facings[gate] : Direction.NORTH;
    }

    /** True when a gate's opening runs along X (i.e. it faces north or south). */
    public static boolean spansX(ArenaMap map, int gate) {
        return outward(map, gate).getAxis() == Direction.Axis.Z;
    }

    /** Where the horde materialises inside the pen, deep enough to have to walk up. */
    public static BlockPos penSpawn(ArenaMap map, int gate) {
        return map.gates()[gate].relative(outward(map, gate), SPAWN_DEPTH);
    }

    /** The volume of the pen, used to find who is currently working on the boards. */
    public static AABB penBounds(ArenaMap map, int gate) {
        BlockPos g = map.gates()[gate];
        BlockPos far = g.relative(outward(map, gate), POCKET_DEPTH);
        return penBox(g, far);
    }

    private static AABB penBox(BlockPos gate, BlockPos far) {
        int minX = Math.min(gate.getX(), far.getX()) - POCKET_HALF_WIDTH;
        int maxX = Math.max(gate.getX(), far.getX()) + POCKET_HALF_WIDTH;
        int minZ = Math.min(gate.getZ(), far.getZ()) - POCKET_HALF_WIDTH;
        int maxZ = Math.max(gate.getZ(), far.getZ()) + POCKET_HALF_WIDTH;
        return new AABB(minX, gate.getY() - 2, minZ, maxX + 1.0, gate.getY() + 7.0, maxZ + 1.0);
    }

    private static BlockPos cell(BlockPos gate, boolean spansX, int off, int dy) {
        return spansX
                ? new BlockPos(gate.getX() + off, gate.getY() + dy, gate.getZ())
                : new BlockPos(gate.getX(), gate.getY() + dy, gate.getZ() + off);
    }

    /** Which gate a clicked block belongs to, or -1. Generous, so the arch works too. */
    public static int gateIndexNear(ArenaMap map, BlockPos pos) {
        BlockPos[] gates = map.gates();
        for (int i = 0; i < gates.length; i++) {
            BlockPos g = gates[i];
            // Deliberately tight on the span axis: the Crypt puts two windows six
            // blocks apart in one wall, and a generous box would mend the wrong one.
            boolean spansX = spansX(map, i);
            int along = Math.abs(spansX ? pos.getX() - g.getX() : pos.getZ() - g.getZ());
            int through = Math.abs(spansX ? pos.getZ() - g.getZ() : pos.getX() - g.getX());
            if (Math.abs(pos.getY() - g.getY()) <= 5 && along <= 2 && through <= 2) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Sizes the state to a map and nails every gate back up. */
    public static void resetFor(ServerLevel level, ArenaMap map) {
        int n = map.gates().length;
        boards = new int[n];
        effort = new float[n];
        if (!map.hasBarricades()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            boards[i] = MAX_BOARDS;
            render(level, map, i);
        }
    }

    /** Boards left on a gate, or 0 if the state has not been sized yet. */
    public static int count(int gate) {
        return gate >= 0 && gate < boards.length ? boards[gate] : 0;
    }

    public static boolean isOpen(int gate) {
        return count(gate) <= 0;
    }

    /** How many gates are currently standing open. */
    public static int openCount() {
        int n = 0;
        for (int b : boards) {
            if (b <= 0) {
                n++;
            }
        }
        return n;
    }

    /** How many boards are missing across every gate - the size of the repair job. */
    public static int missingBoards() {
        int n = 0;
        for (int b : boards) {
            n += MAX_BOARDS - b;
        }
        return n;
    }

    /**
     * Nails one board back on. Returns false if the gate is already sound, so the
     * caller can decline to charge the player for a no-op.
     */
    public static boolean repair(ServerLevel level, ArenaMap map, int gate) {
        if (gate < 0 || gate >= boards.length || boards[gate] >= MAX_BOARDS) {
            return false;
        }
        boards[gate]++;
        effort[gate] = 0.0f; // a fresh board resets the chewing on the one that fell
        render(level, map, gate);
        BlockPos g = map.gates()[gate];
        level.sendParticles(ParticleTypes.CRIT, g.getX() + 0.5, g.getY() + 1.5, g.getZ() + 0.5,
                8, 0.5, 0.8, 0.5, 0.05);
        return true;
    }

    /**
     * Feeds tearing effort into a gate. Returns how many boards came off, so the
     * caller can handle the callouts and the release of whatever was penned.
     */
    public static int addEffort(ServerLevel level, ArenaMap map, int gate, float amount) {
        if (gate < 0 || gate >= boards.length || boards[gate] <= 0 || amount <= 0.0f) {
            return 0;
        }
        effort[gate] += amount;
        int fell = 0;
        while (effort[gate] >= TEAR_COST && boards[gate] > 0) {
            effort[gate] -= TEAR_COST;
            boards[gate]--;
            fell++;
        }
        if (fell > 0) {
            render(level, map, gate);
            BlockPos g = map.gates()[gate];
            level.sendParticles(ParticleTypes.CRIT, g.getX() + 0.5, g.getY() + 1.5, g.getZ() + 0.5,
                    14, 0.6, 0.9, 0.6, 0.12);
            level.sendParticles(ParticleTypes.SMOKE, g.getX() + 0.5, g.getY() + 1.5, g.getZ() + 0.5,
                    6, 0.5, 0.7, 0.5, 0.02);
        }
        return fell;
    }

    /** Effort - in plain-mob-seconds - needed to strip a single board. */
    public static final float TEAR_COST = 4.0f;

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Writes the gate mouth to match the current board count. Only called when a
     * board actually changes, never on a tick.
     */
    public static void render(ServerLevel level, ArenaMap map, int gate) {
        if (gate < 0 || gate >= boards.length || gate >= map.gates().length) {
            return;
        }
        BlockPos g = map.gates()[gate];
        Direction face = outward(map, gate);
        boolean spansX = spansX(map, gate);
        int have = boards[gate];
        for (int i = 0; i < MAX_BOARDS; i++) {
            boolean up = i < have;
            int[] cells = BOARD_CELLS[i];
            for (int c = 0; c < cells.length; c += 2) {
                BlockPos p = cell(g, spansX, cells[c], cells[c + 1]);
                if (!up) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    continue;
                }
                BlockState board = BOARD_WOOD[i].defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, true)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, face)
                        .setValue(BlockStateProperties.HALF,
                                (cells[c + 1] % 2 == 0) ? Half.BOTTOM : Half.TOP);
                level.setBlock(p, board, 3);
            }
        }
    }

    // ------------------------------------------------------------------
    // HUD packing
    // ------------------------------------------------------------------

    /**
     * Packs the whole barricade picture into sixteen bits for the HUD: how many
     * gates stand open, and what fraction of the boards are still up.
     *
     * <p>Aggregate rather than per-gate because the Crypt has eight windows and a
     * per-window gauge at that count is unreadable noise - the callouts and the
     * audio already tell you which one is going.
     *
     * <p>Layout: open count (4 bits) | percent intact (7 bits) | present flag.
     */
    public static int packed(ArenaMap map) {
        if (!map.hasBarricades() || boards.length == 0) {
            return 0;
        }
        int max = boards.length * MAX_BOARDS;
        int have = 0;
        for (int b : boards) {
            have += b;
        }
        int percent = max == 0 ? 0 : Math.round(have * 100.0f / max);
        return PRESENT | ((Math.min(15, openCount()) & 0xF) << 1) | ((percent & 0x7F) << 5);
    }

    /** Low bit: this map has barricades at all. Decoded in AbyssStatePayload. */
    public static final int PRESENT = 1;
}
