package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the maze advancements.
 *
 * <p>Every one of them uses an {@code impossible} trigger and is awarded from
 * code instead. None of these are things vanilla can detect - "escaped the maze
 * without dying", "was outside the Glade when the doors sealed and saw dawn" -
 * so the JSON exists purely to give them a name, an icon and a place on the
 * tree, and the game logic decides who has earned them.
 */
public final class MazeAdvancements {

    public static final String ROOT = "maze/root";
    public static final String FIRST_RUN = "maze/first_run";
    public static final String SURVIVE_NIGHT = "maze/survive_night";
    public static final String FIRST_ESCAPE = "maze/first_escape";
    public static final String CLEAN_ESCAPE = "maze/clean_escape";
    public static final String GRIEVER_SLAYER = "maze/griever_slayer";
    public static final String SERUM = "maze/serum";
    public static final String RACE_WIN = "maze/race_win";

    private MazeAdvancements() {
    }

    /** Awards one, quietly doing nothing if the datapack did not load it. */
    public static void grant(ServerPlayer player, String path) {
        if (player.getServer() == null) {
            return;
        }
        AdvancementHolder holder = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, path));
        if (holder == null) {
            return;
        }
        player.getAdvancements().award(holder, "granted");
    }
}
