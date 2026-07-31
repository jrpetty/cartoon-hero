package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Decides which tick a periodic gadget does its work on.
 *
 * <p>Every gadget here runs on an interval — a counter samples its chest every
 * other tick, a hub refreshes its board every second. The obvious way to write
 * that is {@code gameTime % interval == 0}, but then every counter in the world
 * samples on the same tick and every hub refreshes on the same tick. On a base
 * with a hundred gadgets that piles the entire cost into one tick in twenty and
 * leaves the other nineteen idle, which is exactly the shape of a lag spike.
 *
 * <p>Offsetting each block by a hash of its position spreads the same total work
 * evenly across the interval. The offset is derived from the position alone, so
 * it survives restarts and chunk reloads and never needs saving.
 */
public final class TickPhase {
    private TickPhase() {
    }

    /** True on this block's own slot in the cycle. */
    public static boolean due(Level level, BlockPos pos, int interval) {
        if (interval <= 1) {
            return true;
        }
        return (level.getGameTime() + offset(pos, interval)) % interval == 0L;
    }

    /**
     * A stable 0..interval-1 slot for a position. {@link BlockPos#hashCode()}
     * already mixes the three coordinates, and it changes with every step in x,
     * so neighbouring blocks in a row land in different slots.
     */
    private static int offset(BlockPos pos, int interval) {
        return Math.floorMod(pos.hashCode(), interval);
    }
}
