package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Two places in the Glade you walk up to instead of two commands you remember.
 *
 * <p>The trade sheet and the requisition slate were the two most important
 * screens in the mode and both of them lived behind a slash command. That is
 * fine for an operator and wrong for everybody else: a player who has not been
 * told {@code /maze skills} exists will never find it, and the trade board in
 * the middle of the clearing said nothing about where to spend what it was
 * telling you you had earned.
 *
 * <p>So they are furniture now. A lectern on a stone plinth for your trade, a
 * desk beside the Box for the Glade's order. Both stand where the thing they are
 * about already is, both are lit, and both are signed. The commands still work -
 * they are how an operator checks somebody's sheet without walking over - but
 * nobody needs them.
 */
public final class MazeStations {

    private static final int Y = MazeData.FLOOR_Y;

    /**
     * Beside the trade board, west of it.
     *
     * <p>Sited by checking every other structure in the clearing rather than by
     * eye - the bell tower's first position was silently erased by the Chart
     * Floor's clear square, and once is enough.
     */
    private static final int SKILLS_X = MazeData.SPAWN_X - 12;
    private static final int SKILLS_Z = MazeData.SPAWN_Z + 6;

    /** South-west of the Box, on the way out of the cage. */
    private static final int ORDER_X = MazeData.SPAWN_X - 12;
    private static final int ORDER_Z = MazeData.SPAWN_Z + 10;

    private MazeStations() {
    }

    public static BlockPos skillsDesk() {
        return new BlockPos(SKILLS_X, Y + 1, SKILLS_Z);
    }

    public static BlockPos orderDesk() {
        return new BlockPos(ORDER_X, Y + 1, ORDER_Z);
    }

    public static void build(ServerLevel level) {
        plinth(level, SKILLS_X, SKILLS_Z, Blocks.DEEPSLATE_BRICKS.defaultBlockState().getBlock());
        level.setBlock(skillsDesk(), Blocks.LECTERN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
        sign(level, new BlockPos(SKILLS_X, Y + 2, SKILLS_Z + 1), Direction.SOUTH,
                "§6YOUR TRADE", "§0What you", "§0have learned.", "§8Right-click");

        plinth(level, ORDER_X, ORDER_Z, Blocks.POLISHED_DEEPSLATE.defaultBlockState().getBlock());
        level.setBlock(orderDesk(), Blocks.SMITHING_TABLE.defaultBlockState(), 2);
        sign(level, new BlockPos(ORDER_X, Y + 2, ORDER_Z + 1), Direction.SOUTH,
                "§6THE SLATE", "§0What the Box", "§0brings up.", "§8Right-click");
    }

    /** A lit two-block base, so a desk in a field reads as somewhere to go. */
    private static void plinth(ServerLevel level, int x, int z, net.minecraft.world.level.block.Block stone) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, Y, z + dz), stone.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(x - 1, Y + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x + 1, Y + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
    }

    /**
     * Right-clicking one of them.
     *
     * @return true if this was a station, and the click has been handled
     */
    public static boolean onUse(ServerLevel level, ServerPlayer who, BlockPos at) {
        if (at.equals(skillsDesk())) {
            ModNetworking.sendSkills(who);
            level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS, 0.9F, 1.0F);
            return true;
        }
        if (at.equals(orderDesk())) {
            if (MazeJobs.get(level).jobOf(who.getUUID()) == null) {
                // The slate is open to anybody, but it is worth saying: a trade
                // with nobody in it is a quota nobody fills.
                who.displayClientMessage(Component.literal(
                        "§7You have no trade yet. §8The board is by the bell."), true);
            }
            ModNetworking.sendOrders(who);
            level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS, 0.9F, 0.8F);
            return true;
        }
        return false;
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
