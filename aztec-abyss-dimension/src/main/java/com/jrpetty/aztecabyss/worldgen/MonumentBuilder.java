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

    public static void build(ServerLevel level) {
        BlockPos p = AztecAbyssConstants.MONUMENT_POS;
        int floorY = p.getY();
        int x = p.getX();
        int z = p.getZ();

        // Backing slab + gold frame (one block east of the sign face).
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean edge = (y == floorY + 1 || y == floorY + 4 || dz == -1 || dz == 1);
                level.setBlock(new BlockPos(x + 1, y, z + dz), edge ? TRIM : FACE, 3);
            }
        }
        // A plinth the monument stands on.
        for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(new BlockPos(x, floorY, z + dz), BASE, 3);
            level.setBlock(new BlockPos(x + 1, floorY, z + dz), BASE, 3);
        }

        AbyssStats stats = AbyssStats.get(level.getServer());

        writeSign(level, new BlockPos(x, floorY + 3, z), new String[]{
                "", "§6§l⚔ TOP", "§6§lSURVIVORS ⚔", ""});
        writeSign(level, new BlockPos(x, floorY + 2, z), leaderboardLines(stats, false, "§6§lSOLO §7by time"));
        writeSign(level, new BlockPos(x, floorY + 1, z), leaderboardLines(stats, true, "§6§lCO-OP §7by time"));
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
