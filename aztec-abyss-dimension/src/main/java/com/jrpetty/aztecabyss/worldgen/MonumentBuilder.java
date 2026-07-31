package com.jrpetty.aztecabyss.worldgen;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.data.AbyssStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and live-updates the leaderboard monument beside the arrival spawn: a
 * dark polished-blackstone slab with gold trim, its front carved with the top
 * survivors in both solo and co-op, ranked by survival time. Rewritten whenever
 * a run ends so it always shows the current standings.
 */
public final class MonumentBuilder {

    private static final BlockState TRIM = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState FACE = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState BASE = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();

    private MonumentBuilder() {
    }

    /** Rebuilds every monument in the dimension - one per arena. */
    public static void build(ServerLevel level) {
        buildAt(level, AztecAbyssConstants.MONUMENT_POS);
        buildAt(level, BRIDGE_MONUMENT);
    }

    /**
     * The bridge's own monument, standing in the fort courtyard so defenders on
     * that map get the same standings the temple crowd sees.
     */
    public static final BlockPos BRIDGE_MONUMENT = new BlockPos(
            BridgeBuilder.CENTER_X - 9, BridgeBuilder.DECK_Y, BridgeBuilder.ISLAND_CENTER_Z - 4);

    private static void buildAt(ServerLevel level, BlockPos p) {
        int floorY = p.getY();
        int x = p.getX();
        int z = p.getZ();

        // Backing slab + gold frame, now three columns wide: overall, then one
        // per arena, so each battlefield carries its own standings.
        ArenaMap[] maps = ArenaMap.values();
        int columns = 1 + maps.length;
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            for (int dz = -1; dz <= columns * 2 - 1; dz++) {
                boolean edge = (y == floorY + 1 || y == floorY + 4 || dz == -1 || dz == columns * 2 - 1);
                level.setBlock(new BlockPos(x + 1, y, z + dz), edge ? TRIM : FACE, 3);
            }
        }
        // A plinth the monument stands on.
        for (int dz = -1; dz <= columns * 2 - 1; dz++) {
            level.setBlock(new BlockPos(x, floorY, z + dz), BASE, 3);
            level.setBlock(new BlockPos(x + 1, floorY, z + dz), BASE, 3);
        }

        AbyssStats stats = AbyssStats.get(level.getServer());

        // Column 0: overall solo / co-op standings.
        writeSign(level, new BlockPos(x, floorY + 3, z), new String[]{
                "", "§6§l⚔ TOP", "§6§lSURVIVORS ⚔", ""});
        writeSign(level, new BlockPos(x, floorY + 2, z), leaderboardLines(stats, false, "§6§lSOLO §7by time"));
        writeSign(level, new BlockPos(x, floorY + 1, z), leaderboardLines(stats, true, "§6§lCO-OP §7by time"));

        // One column per arena, offset along +Z.
        for (int i = 0; i < maps.length; i++) {
            int cz = z + (i + 1) * 2;
            ArenaMap map = maps[i];
            String shortName = map == ArenaMap.BRIDGE ? "THE BRIDGE" : "THE TEMPLE";
            writeSign(level, new BlockPos(x, floorY + 3, cz), new String[]{
                    "", "§b§l" + shortName, "§7best rounds", ""});
            writeSign(level, new BlockPos(x, floorY + 2, cz), mapLeaderboardLines(stats, i, 0));
            writeSign(level, new BlockPos(x, floorY + 1, cz), mapLeaderboardLines(stats, i, 3));
        }
    }

    /** Ranks 1-3 (offset 0) or 4-6 (offset 3) on one specific arena. */
    private static String[] mapLeaderboardLines(AbyssStats stats, int mapId, int offset) {
        List<Map.Entry<UUID, AbyssStats.Entry>> top = stats.topOnMap(offset + 3, mapId);
        String[] lines = new String[]{"", "§8—", "§8—", "§8—"};
        if (offset == 0) {
            lines[0] = "§e#1 - #3";
        } else {
            lines[0] = "§e#4 - #6";
        }
        for (int i = offset; i < top.size() && i < offset + 3; i++) {
            AbyssStats.Entry e = top.get(i).getValue();
            String name = e.name().length() > 9 ? e.name().substring(0, 9) : e.name();
            lines[i - offset + 1] = "§e#" + (i + 1) + " §f" + name + " §7R" + e.bestRoundOnMap(mapId);
        }
        return lines;
    }

    private static String[] leaderboardLines(AbyssStats stats, boolean multiplayer, String header) {
        List<Map.Entry<UUID, AbyssStats.Entry>> top = stats.top(3, multiplayer);
        String[] lines = new String[]{header, "§8—", "§8—", "§8—"};
        for (int i = 0; i < top.size() && i < 3; i++) {
            AbyssStats.Entry e = top.get(i).getValue();
            int secs = multiplayer ? e.mpBestSeconds() : e.soloBestSeconds();
            int rnd = multiplayer ? e.mpBestRound() : e.soloBestRound();
            String name = e.name().length() > 9 ? e.name().substring(0, 9) : e.name();
            lines[i + 1] = "§e#" + (i + 1) + " §f" + name + " §7" + fmt(secs) + " R" + rnd;
        }
        return lines;
    }

    private static String fmt(int seconds) {
        return (seconds / 60) + "m" + (seconds % 60) + "s";
    }

    private static void writeSign(ServerLevel level, BlockPos pos, String[] lines) {
        BlockState sign = Blocks.WARPED_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        level.setBlock(pos, sign, 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            be.updateText(text -> {
                SignText t = text;
                for (int i = 0; i < lines.length && i < 4; i++) {
                    t = t.setMessage(i, Component.literal(lines[i]));
                }
                return t;
            }, true);
        }
    }
}
