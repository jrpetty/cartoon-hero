package com.jrpetty.aztecabyss.engine;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

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
 *
 * <p>The password is the way in for everyone else. Operators skip it, because
 * they can already do all of this and more; anybody else types it once and is
 * remembered from then on. That is what makes Creator shareable without making
 * it open: the server owner hands the word to the people they want building, and
 * to nobody else.
 */
public final class MapCreator {

    /**
     * Where you land.
     *
     * <p>The Workshop is a genuine flat world now - bedrock, stone, dirt, grass,
     * from {@code y=0} up - rather than a void with a small platform floating in
     * it. The platform was a workable answer to "you need something to stand on"
     * and a bad answer to everything after that: you could only build on the pad,
     * anything you walked off fell out of the world, and an author's first
     * experience of the map editor was dying in it.
     *
     * <p>Ground level is {@code y=4}, so a build starts at 5 and has 378 blocks of
     * headroom. Whole numbers near zero, which matters when the coordinates end up
     * in a wand selection you have to reason about.
     */
    private static final BlockPos PAD = new BlockPos(0, 5, 0);

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

    /** Set once a player has typed the password correctly. */
    private static final String UNLOCKED_TAG = "aztecabyss_creator_unlocked";

    private MapCreator() {
    }

    /** Whether this player may enter Creator at all. */
    public static boolean mayEnter(ServerPlayer player) {
        return player.hasPermissions(2)
                || player.getPersistentData().getBoolean(UNLOCKED_TAG);
    }

    /**
     * Checks a password and remembers the answer.
     *
     * <p>Remembered on the player rather than asked for every time. A gate you
     * retype on every visit stops being a gate and starts being a chore people
     * work around - and the thing it is protecting is a build tool, not a bank.
     *
     * <p>Compared with {@code MessageDigest.isEqual} rather than {@code equals}
     * so the comparison takes the same time whatever the guess. That is barely
     * worth doing against a Minecraft chat prompt and it costs nothing, which is
     * the right trade for any password comparison.
     *
     * @return true if the word was right
     */
    public static boolean unlock(ServerPlayer player, String attempt) {
        String expected = com.jrpetty.aztecabyss.config.AbyssConfig.CREATOR_PASSWORD.get();
        boolean ok = java.security.MessageDigest.isEqual(
                attempt.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (ok) {
            player.getPersistentData().putBoolean(UNLOCKED_TAG, true);
        }
        return ok;
    }

    /** Forgets a player's unlock, for a server owner who has changed the word. */
    public static void lock(ServerPlayer player) {
        player.getPersistentData().putBoolean(UNLOCKED_TAG, false);
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
        if (!mayEnter(player)) {
            return "Map Creator is locked. Type §f/creator <password>§r to open it — "
                    + "ask whoever runs this server for the word.";
        }
        ServerLevel shop = player.getServer().getLevel(AztecAbyssConstants.WORKSHOP_LEVEL_KEY);
        if (shop == null) {
            return "The Workshop dimension is not loaded.";
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
