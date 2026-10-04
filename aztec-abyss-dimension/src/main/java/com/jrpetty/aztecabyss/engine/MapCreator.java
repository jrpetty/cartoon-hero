package com.jrpetty.aztecabyss.engine;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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

    /**
     * What a player brought to the Workshop, kept on them until they leave:
     * where they were, the game mode they were in, and their inventory.
     */
    private static final String RETURN_TAG = "aztecabyss_workshop_return";

    /**
     * The way out: a lodestone a few steps in front of the pad, signed.
     *
     * <p>The Workshop had no exit. An operator could type their way out; a
     * player let in by the password could not run a single one of the commands
     * that would do it, and was left in creative mode in a flat world with no
     * door. Right-clicking this stone takes you home with your own things back.
     */
    public static final BlockPos EXIT = new BlockPos(0, 5, 4);

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

    /** When each player last guessed, so the box cannot be used to guess fast. */
    private static final java.util.Map<java.util.UUID, Long> LAST_GUESS = new java.util.HashMap<>();

    /**
     * A guess from the password box. Right, and they go straight in; wrong, and
     * the box comes back saying so. One guess a second at most - the word is a
     * gate, and a gate that answers as fast as it is asked is a lock with the
     * combination printed on it for anybody patient.
     */
    public static void tryUnlock(ServerPlayer player, String attempt) {
        long now = System.currentTimeMillis();
        Long last = LAST_GUESS.put(player.getUUID(), now);
        boolean ok = (last == null || now - last >= 1000L) && unlock(player, attempt);
        if (!ok) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.jrpetty.aztecabyss.network.CreatorGatePayload(true));
            return;
        }
        LAST_GUESS.remove(player.getUUID());
        String error = enter(player, true);
        if (error != null) {
            player.displayClientMessage(Component.literal("§c" + error), false);
        }
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
            return "Map Creator is locked — ask whoever runs this server for the word.";
        }
        ServerLevel shop = player.getServer().getLevel(AztecAbyssConstants.WORKSHOP_LEVEL_KEY);
        if (shop == null) {
            return "The Workshop dimension is not loaded.";
        }

        // Coming in from outside: put what they brought somewhere safe first.
        // Creative hands out anything, and nothing made in here may follow a
        // player back into a survival world.
        boolean arriving = !player.level().dimension().equals(AztecAbyssConstants.WORKSHOP_LEVEL_KEY);
        if (arriving) {
            stash(player);
        }

        player.teleportTo(shop, PAD.getX() + 0.5, PAD.getY(), PAD.getZ() + 0.5,
                java.util.Set.of(), 0.0F, 0.0F);
        player.setGameMode(GameType.CREATIVE);
        placeExit(shop);

        if (!hasWand(player)) {
            give(player, BuildTools.wand());
        }
        if (withKit && arriving) {
            for (String kind : STARTER_KIT) {
                give(player, BuildTools.markerSign(kind, BuildTools.hintFor(kind)));
            }
            welcome(player);
        } else {
            player.displayClientMessage(Component.literal(
                    "§6The Workshop. §7Right-click the air with the Map Wand for the Creator Console."), false);
        }
        return null;
    }

    /** Whether a player is standing in the Workshop. */
    public static boolean inWorkshop(ServerPlayer player) {
        return player.level().dimension().equals(AztecAbyssConstants.WORKSHOP_LEVEL_KEY);
    }

    private static boolean hasWand(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (BuildTools.isWand(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Puts away where a player was, how they were playing and what they carried. */
    private static void stash(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        tag.putString("mode", player.gameMode.getGameModeForPlayer().getName());
        tag.put("inv", player.getInventory().save(new ListTag()));
        player.getPersistentData().put(RETURN_TAG, tag);
        player.getInventory().clearContent();
    }

    /**
     * Takes a player out of the Workshop: back where they came in from, in the
     * game mode they had, carrying exactly what they carried - nothing made in
     * here comes with them.
     *
     * <p>A player who went in before the Workshop kept anything (an older
     * world) has nothing stashed. They are sent to the world spawn in the
     * server's default game mode and keep what they hold, because guessing
     * which of their things are "really" theirs would be worse.
     */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !inWorkshop(player)) {
            return;
        }
        // A play-test going on in here ends with the author leaving it.
        EngineArena run = EngineArena.active();
        if (run != null && EngineArena.isRunning() && run.level() == player.level() && run.isParticipant(player)) {
            EngineArena.stop(true);
        }
        BuildTools.clearSelection(player);

        CompoundTag tag = player.getPersistentData().getCompound(RETURN_TAG);
        boolean stashed = player.getPersistentData().contains(RETURN_TAG);
        ServerLevel dest = null;
        if (stashed) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
            if (dim != null) {
                dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            }
        }
        if (dest == null || dest.dimension().equals(AztecAbyssConstants.WORKSHOP_LEVEL_KEY)) {
            dest = server.overworld();
            stashed = stashed && dest != null;
        }
        GameType mode = stashed
                ? GameType.byName(tag.getString("mode"), server.getDefaultGameType())
                : server.getDefaultGameType();
        if (stashed) {
            player.getInventory().clearContent();
            player.getInventory().load(tag.getList("inv", Tag.TAG_COMPOUND));
            player.getPersistentData().remove(RETURN_TAG);
        }
        player.setGameMode(mode);
        if (stashed && tag.contains("x")) {
            player.teleportTo(dest, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    java.util.Set.of(), tag.getFloat("yaw"), tag.getFloat("pitch"));
        } else {
            BlockPos spawn = dest.getSharedSpawnPos();
            player.teleportTo(dest, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    java.util.Set.of(), player.getYRot(), 0.0F);
        }
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(stashed
                ? "§6Back from the Workshop. §7Everything you carried in is back in your pockets."
                : "§6Back from the Workshop."), false);
    }

    /**
     * Stands the way out where every arrival can see it: a lodestone a few
     * steps in front of the pad, a lantern on it, a sign on its face. Only into
     * empty air - a builder who has put something on that spot keeps it, and
     * still has the console's Leave button.
     */
    private static void placeExit(ServerLevel shop) {
        if (shop.getBlockState(EXIT).is(Blocks.LODESTONE)) {
            return;
        }
        if (!shop.getBlockState(EXIT).isAir() || !shop.getBlockState(EXIT.above()).isAir()
                || !shop.getBlockState(EXIT.north()).isAir()) {
            return;
        }
        shop.setBlock(EXIT, Blocks.LODESTONE.defaultBlockState(), 3);
        shop.setBlock(EXIT.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        BlockPos face = EXIT.north();
        shop.setBlock(face, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, Direction.NORTH), 3);
        if (shop.getBlockEntity(face) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            Component[] lines = {
                    Component.literal("§6LEAVE THE"),
                    Component.literal("§6WORKSHOP"),
                    Component.literal("§7right-click"),
                    Component.literal("§7the stone")};
            net.minecraft.world.level.block.entity.SignText text = sign.getFrontText();
            for (int i = 0; i < lines.length; i++) {
                text = text.setMessage(i, lines[i]);
            }
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            var state = shop.getBlockState(face);
            shop.sendBlockUpdated(face, state, state, 3);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * The first thing an author reads.
     *
     * <p>A few lines, in the order the work actually happens. Anything longer
     * gets skipped, and an author who skips the instructions has to discover the
     * wand by accident. None of them is a command: the console and the exit
     * stone are things you reach with your hands.
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
                "§7Right-click the air with the wand for the §fCreator Console§7: "
                        + "check the map, play-test it, save it and put it on the portal."), false);
        player.displayClientMessage(Component.literal(
                "§7The lodestone in front of you takes you home, your own things back in your pockets."), false);
        player.displayClientMessage(Component.literal(
                "§7Every other marker is on the console's Markers page."), false);
    }
}
