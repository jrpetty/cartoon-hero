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

    /** Between the other two desks, completing the little civic row. */
    private static final int CHART_X = MazeData.SPAWN_X - 16;
    private static final int CHART_Z = MazeData.SPAWN_Z + 8;

    private MazeStations() {
    }

    public static BlockPos skillsDesk() {
        return new BlockPos(SKILLS_X, Y + 1, SKILLS_Z);
    }

    public static BlockPos orderDesk() {
        return new BlockPos(ORDER_X, Y + 1, ORDER_Z);
    }

    public static BlockPos chartTable() {
        return new BlockPos(CHART_X, Y + 1, CHART_Z);
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

        plinth(level, CHART_X, CHART_Z, Blocks.DEEPSLATE_TILES.defaultBlockState().getBlock());
        level.setBlock(chartTable(), Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 2);
        sign(level, new BlockPos(CHART_X, Y + 2, CHART_Z + 1), Direction.SOUTH,
                "§6THE CHART TABLE", "§0Pin what you", "§0have walked.", "§8Chartwrights only");
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
        if (at.equals(chartTable())) {
            pinChart(level, who);
            return true;
        }
        return false;
    }

    /**
     * Pins a living copy of a Runner's Chart to the table.
     *
     * <p>Copies of a vanilla map share their data, so the pinned sheet keeps
     * filling in as its Runner keeps walking - the Glade watches the picture
     * grow without anyone standing at the table. It hangs in an item frame,
     * which is the point: the group's knowledge becomes a physical thing that
     * lives in the safe ground, rather than dying in a pocket in a corridor.
     */
    private static void pinChart(ServerLevel level, ServerPlayer who) {
        if (MazeSkills.rankOf(level, who.getUUID(), "chartwright") < 1) {
            who.displayClientMessage(Component.literal(
                    "§7The table is for Chartwrights. §8Learn it at the trade desk."), true);
            level.playSound(null, chartTable(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS, 0.7F, 0.6F);
            return;
        }
        net.minecraft.world.item.ItemStack held = who.getMainHandItem();
        if (!held.is(net.minecraft.world.item.Items.FILLED_MAP)) {
            who.displayClientMessage(Component.literal(
                    "§7Hold your Runner's Chart to pin a copy."), true);
            return;
        }
        net.minecraft.world.item.ItemStack copy = held.copy();
        copy.setCount(1);
        copy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§bThe Glade's Chart §7— " + who.getGameProfile().getName()));
        BlockPos framePos = chartTable().above();
        var frames = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                new net.minecraft.world.phys.AABB(framePos), f -> true);
        net.minecraft.world.entity.decoration.ItemFrame frame =
                frames.isEmpty() ? null : frames.get(0);
        if (frame == null) {
            frame = new net.minecraft.world.entity.decoration.ItemFrame(
                    level, framePos, Direction.UP);
            frame.setInvulnerable(true);
            level.addFreshEntity(frame);
        }
        frame.setItem(copy);
        level.playSound(null, chartTable(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.BLOCKS, 1.0F, 1.2F);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal("§b✦ "
                    + who.getGameProfile().getName()
                    + " pinned their chart to the table."), false);
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
