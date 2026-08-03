package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The bell, and the last two minutes.
 *
 * <p>The doors closing was a single line of chat sixty seconds out. If you were
 * deep in the maze with something chasing you, that line arrived, scrolled, and
 * was gone - and the next thing you knew about the deadline was the sound of it
 * sealing. A warning you can miss is not a warning, and the one deadline in this
 * mode that kills people was the one thing it said quietest.
 *
 * <p>So the Glade has a bell, and for the last two minutes of every day it
 * tolls. It is not a message. It is a sound that arrives whether you are looking
 * at the screen or not, from a place you know the direction of.
 *
 * <h2>Why it speeds up</h2>
 *
 * <p>Every ten seconds at first, every five inside a minute, every second inside
 * twenty. The cadence is the information: you do not have to count, because the
 * gaps tell you how bad it is. A metronome that never changes is something you
 * stop hearing after the second toll.
 *
 * <h2>Why it is quieter far away</h2>
 *
 * <p>Played at each listener rather than at the tower, so it genuinely reaches
 * the far corners of a 576-block map - but scaled by how far out they are, and
 * pitched down with distance. Somebody in the clearing hears a bell rung nearby;
 * somebody forty cells out hears something deep and far off that they have to
 * stop and listen for. Both of them know exactly what it is.
 */
public final class MazeBell {

    private static final int Y = MazeData.FLOOR_Y;
    /**
     * South-west of the Box, and deliberately south of z=283.
     *
     * <p>The Chart Floor clears a 44-block square from the Glade's minimum
     * corner - x and z both 240 to 283 - and it is laid <em>after</em> this, so
     * anything standing in that square is erased and paved over. The first
     * position tried was inside it, and the tower would simply never have
     * existed.
     */
    private static final int TOWER_X = MazeData.SPAWN_X - 6;
    private static final int TOWER_Z = MazeData.SPAWN_Z + 10;

    /** The last second the bell was rung on, so one toll is one toll. */
    private static int lastTolledAt = -1;

    private MazeBell() {
    }

    public static void reset() {
        lastTolledAt = -1;
    }

    public static BlockPos bell() {
        return new BlockPos(TOWER_X, Y + 5, TOWER_Z);
    }

    /**
     * A stone post with the bell hung on top and a lantern under it.
     *
     * <p>Deliberately the tallest thing in the clearing after the Box cage, and
     * lit, so that "head for the bell" works as a direction at night.
     */
    public static void build(ServerLevel level) {
        // A plinth, so it does not read as a fence post somebody abandoned.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(TOWER_X + dx, Y, TOWER_Z + dz),
                        Math.abs(dx) + Math.abs(dz) == 2
                                ? Blocks.COBBLESTONE.defaultBlockState()
                                : Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int dy = 1; dy <= 4; dy++) {
            level.setBlock(new BlockPos(TOWER_X, Y + dy, TOWER_Z),
                    dy == 4 ? Blocks.STONE_BRICK_WALL.defaultBlockState()
                            : Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
        }
        level.setBlock(bell(), Blocks.BELL.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        level.setBlock(new BlockPos(TOWER_X + 1, Y + 3, TOWER_Z),
                Blocks.LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(TOWER_X - 1, Y + 3, TOWER_Z),
                Blocks.LANTERN.defaultBlockState(), 2);
    }

    // ------------------------------------------------------------------

    /** Seconds until the doors seal, or -1 if they are already shut. */
    private static int secondsToSeal(MazeClock clock) {
        if (clock.isNight()) {
            return -1;
        }
        return (MazeClock.dayTicks() - clock.phase()) / 20;
    }

    /**
     * Should the bell sound on this second?
     *
     * <p>The gaps are the message. Ten seconds apart is "you have time", five is
     * "start heading back", every second is "run".
     */
    private static boolean tollsOn(int left) {
        if (left > 60) {
            return left % 10 == 0;
        }
        if (left > 20) {
            return left % 5 == 0;
        }
        return true;
    }

    /**
     * Rings it, once a second at most, for the last stretch of the day.
     *
     * <p>Called from the per-second block of the runtime tick. Guarded on the
     * second it last rang rather than on a boolean, so a server restart mid-toll
     * cannot leave the bell silent for the rest of the day.
     */
    public static void tick(ServerLevel level, MazeClock clock) {
        int window = AbyssConfig.MAZE_BELL_SECONDS.get();
        if (window <= 0) {
            return;
        }
        int left = secondsToSeal(clock);
        if (left < 0 || left > window) {
            return;
        }
        if (left == lastTolledAt || !tollsOn(left)) {
            return;
        }
        lastTolledAt = left;

        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - MazeData.SPAWN_X;
            double dz = p.getZ() - MazeData.SPAWN_Z;
            double away = Math.sqrt(dx * dx + dz * dz);
            // Half the map away is as far as it gets; past that it does not get
            // any fainter, because a bell you cannot hear at all is the bug this
            // is fixing.
            double reach = Math.min(1.0, away / (MazeData.SPAN / 2.0));

            float volume = (float) (1.7 - 1.2 * reach);
            // Deeper the further out you are. The same bell, heard across a lot
            // of stone.
            float pitch = (float) (1.0 - 0.35 * reach);
            if (left <= 20) {
                // The last twenty seconds ring a shade higher and harder. It is
                // the same bell being rung by somebody who has started to panic.
                volume += 0.4F;
                pitch += 0.12F;
            }
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK,
                    SoundSource.BLOCKS, volume, pitch);

            // The count itself, but only for the people it is actually about.
            // Somebody standing in the clearing does not need a countdown.
            if (!MazeData.inGlade(p.blockPosition().getX() / MazeData.CELL,
                    p.blockPosition().getZ() / MazeData.CELL)) {
                p.displayClientMessage(Component.literal(
                        (left <= 20 ? "§c§l" : "§6") + "┰ THE DOORS — "
                                + (left / 60) + ":" + (left % 60 < 10 ? "0" : "") + (left % 60)), true);
            }
        }
    }
}
