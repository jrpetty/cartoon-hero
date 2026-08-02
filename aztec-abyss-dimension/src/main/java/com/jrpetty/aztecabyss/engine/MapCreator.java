package com.jrpetty.aztecabyss.engine;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Map Creator - the fourth thing on the menu, and the only one that is not a
 * fight.
 *
 * <p>The authoring tools existed before this class did, but they were reachable
 * only by knowing that {@code /arena workshop} was a command worth typing. That
 * put the engine's whole point behind a piece of knowledge nobody arriving at the
 * mod has: the picker offered three battlefields and said nothing about the fact
 * that you can build a fourth.
 *
 * <p>So it becomes a mode. It sits on the same screen as the Temple and the
 * Maze, it is entered the same way, and it hands you the tools rather than
 * expecting you to ask for them by name.
 *
 * <h2>Why this is gated to operators</h2>
 *
 * <p>Entering Creator means creative mode and a tool that rewrites regions of the
 * world. On a singleplayer world the player is already an operator and nothing
 * changes. On a server, a button that hands creative to anyone who clicks it is
 * not a game mode, it is a way to take a server apart - so the button is visible
 * to everyone and refuses politely for anyone who should not have it, which is
 * better than hiding it and leaving them wondering.
 */
public final class MapCreator {

    /** Half-width of the stone pad you arrive on. */
    private static final int PAD_HALF = 8;
    private static final BlockPos PAD = new BlockPos(0, 64, 0);

    /**
     * The markers worth having in hand on arrival.
     *
     * <p>Not all seventeen - a hotbar full of signs is a worse start than an empty
     * one, because the first thing you do is throw most of them away. These six
     * are the ones a map cannot be a map without: somewhere to stand, ways in, a
     * way for them to arrive unseen, something to buy, something to buy your way
     * into, and a way to win.
     */
    private static final String[] STARTER_KIT = {
            "spawn", "horde", "pen", "dealer", "door", "extract"
    };

    private MapCreator() {
    }

    /**
     * Puts a player into Creator, building the arrival pad if it is not there.
     *
     * @param withKit whether to hand over the wand and starter markers - true when
     *                arriving through the picker, false for {@code /arena
     *                workshop}, where an author returning to a build already has
     *                their tools and does not want six more signs
     * @return null on success, or the reason it could not happen
     */
    public static String enter(ServerPlayer player, boolean withKit) {
        if (player.getServer() == null) {
            return "No server.";
        }
        if (!player.hasPermissions(2)) {
            return "Map Creator needs operator permission — it hands out creative mode "
                    + "and a tool that rewrites the world.";
        }
        ServerLevel shop = player.getServer().getLevel(AztecAbyssConstants.WORKSHOP_LEVEL_KEY);
        if (shop == null) {
            return "The Workshop dimension is not loaded.";
        }

        // The Workshop is genuinely empty, so without this you arrive in a void and
        // fall out of your own map before you have built any of it.
        for (int x = -PAD_HALF; x <= PAD_HALF; x++) {
            for (int z = -PAD_HALF; z <= PAD_HALF; z++) {
                shop.setBlock(PAD.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }

        player.teleportTo(shop, PAD.getX() + 0.5, PAD.getY(), PAD.getZ() + 0.5,
                java.util.Set.of(), 0.0F, 0.0F);
        player.setGameMode(GameType.CREATIVE);

        if (withKit) {
            give(player, BuildTools.wand());
            for (String kind : STARTER_KIT) {
                give(player, BuildTools.markerSign(kind, BuildTools.hintFor(kind)));
            }
            welcome(player);
        } else {
            player.displayClientMessage(Component.literal(
                    "§6The Workshop. §7Build here, then §f/arena wand§7 to mark out the map."), false);
        }
        return null;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * The first thing an author reads.
     *
     * <p>Four lines, in the order the work actually happens. Anything longer gets
     * skipped, and an author who skips the instructions has to discover the wand
     * by accident.
     */
    private static void welcome(ServerPlayer player) {
        player.displayClientMessage(Component.literal("§6§lMAP CREATOR"), false);
        player.displayClientMessage(Component.literal(
                "§7Build whatever you like. Then place the signs — §fthat is how the "
                        + "engine learns what your build means§7."), false);
        player.displayClientMessage(Component.literal(
                "§7Mark it out with the §6Map Wand§7 — left-click one corner, "
                        + "right-click the other."), false);
        player.displayClientMessage(Component.literal(
                "§f/arena validate §8→ §f/arena test §8→ §f/arena create <name>"), false);
        player.displayClientMessage(Component.literal(
                "§8/arena marker <kind> for the other eleven. /arena stop to leave."), false);
    }
}
