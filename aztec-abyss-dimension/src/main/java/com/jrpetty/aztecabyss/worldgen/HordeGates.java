package com.jrpetty.aztecabyss.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * Where the horde comes in: the shape of an arena's gates.
 *
 * <p>This used to be the geometry half of the boarded-gate system, and it is
 * all that system left behind. The boards themselves went first - every gate
 * on every arena now stands open - and the Outpost, the one map that still
 * leaned on pens and breaches, went with them. What survives is the part the
 * Temple's generator actually needs: which way a gate faces, and how big the
 * gatehouse behind it is.
 *
 * <p>Plus one piece of housekeeping. A world that stood up while gates were
 * boarded can still have planks nailed across its arches, and nothing would
 * ever take them down; {@link #clearLeftoverBoards} does, touching trapdoors
 * in the gate mouth and nothing else, so it can never eat real scenery.
 */
public final class HordeGates {

    /** How far the sealed gatehouse extends out behind the arch. */
    public static final int POCKET_DEPTH = 6;
    /** Half-width of the gatehouse, measured from the gate's centre line. */
    public static final int POCKET_HALF_WIDTH = 3;

    /**
     * Where planks used to sit in a gate mouth, as {off, dy} pairs. Kept only to
     * know where to look when clearing a world built while gates were boarded.
     */
    private static final int[][] OLD_BOARD_CELLS = {
            {-1, 0, 0, 0, 1, 0},
            {-1, 1, 0, 1},
            {0, 2, 1, 2},
            {-1, 3, 0, 3, 1, 3},
            {1, 1, -1, 2},
    };

    private HordeGates() {
    }

    /** The direction pointing out of the arena through a gate. */
    public static Direction outward(ArenaMap map, int gate) {
        Direction[] facings = map.gateFacings();
        return gate >= 0 && gate < facings.length ? facings[gate] : Direction.NORTH;
    }

    /** True when a gate's opening runs along X (i.e. it faces north or south). */
    public static boolean spansX(ArenaMap map, int gate) {
        return outward(map, gate).getAxis() == Direction.Axis.Z;
    }

    /** Takes down any planks a boarded-era world still has across its arches. */
    public static void clearLeftoverBoards(ServerLevel level, ArenaMap map) {
        BlockPos[] gates = map.gates();
        for (int i = 0; i < gates.length; i++) {
            BlockPos g = gates[i];
            boolean spansX = spansX(map, i);
            for (int[] cells : OLD_BOARD_CELLS) {
                for (int c = 0; c < cells.length; c += 2) {
                    BlockPos p = spansX
                            ? new BlockPos(g.getX() + cells[c], g.getY() + cells[c + 1], g.getZ())
                            : new BlockPos(g.getX(), g.getY() + cells[c + 1], g.getZ() + cells[c]);
                    if (level.getBlockState(p).getBlock() instanceof TrapDoorBlock) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
