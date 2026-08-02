package com.jrpetty.aztecabyss.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Maps that have been put on the portal.
 *
 * <p>Making a map and being able to <em>play</em> it were two different things.
 * {@code /arena create} wrote a file and {@code /arena test} ran it where you were
 * standing, but there was no way to make one a thing you choose from the portal
 * alongside the Temple and the Bridge - so every custom map was something you had
 * to be stood in the right place and know the right command to reach. A map nobody
 * can find from the menu is a map nobody plays.
 *
 * <p>Publishing gives one a permanent home. The Abyss already keeps its three
 * arenas in a single dimension, far apart on the X axis; a published map gets the
 * next free slot in that same layout, is stamped there once, and from then on is
 * somewhere you can be sent. It becomes a place rather than a file.
 *
 * <h2>Why the engine runs them and not the round manager</h2>
 *
 * <p>{@code ArenaMap} is an enum, and the three hand-built arenas are wired
 * through it into the round manager, bosses, scoring and all. A published map
 * cannot be an enum constant, and turning that enum into a registry to make room
 * for one would be a large refactor of the oldest code in the mod for no gain -
 * because the engine already does every part of this. So published maps run on
 * {@link EngineArena}, with their own rulesets, markers and rounds. The round
 * manager keeps the three arenas that were hand-written for it, and everything
 * authored runs on the thing built for authoring.
 *
 * <h2>The folder</h2>
 *
 * <p>Everything lands in {@code <world>/aztecabyss/}, which exists to be opened.
 * Minecraft's own {@code generated/} directory is where the structure manager
 * insists on keeping {@code .nbt} files and is not somewhere anybody would think
 * to look; this is a plain folder with a readable JSON per map and a README
 * saying what it is.
 */
public final class PublishedMaps {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Where published maps live on the X axis.
     *
     * <p>Clear of the Temple at the origin, the Bridge at 2000 and the Outpost,
     * with room for a very large map in every slot and a wide gap between them so
     * nothing can ever hear or see its neighbour.
     */
    private static final int SLOT_ORIGIN_X = 20000;
    private static final int SLOT_SPACING = 2048;
    /** Where a stamped map's low corner sits vertically. */
    public static final int SLOT_Y = 64;

    /** One published map. */
    public record Entry(String name, String title, String author, String blurb,
                        String difficulty, String ruleset, int slot,
                        int sizeX, int sizeY, int sizeZ) {

        /** The low corner this map was stamped at. */
        public BlockPos origin() {
            return new BlockPos(SLOT_ORIGIN_X + slot * SLOT_SPACING, SLOT_Y, 0);
        }

        /** The box the engine should scan and play. */
        public BoundingBox bounds() {
            BlockPos o = origin();
            return new BoundingBox(o.getX(), o.getY(), o.getZ(),
                    o.getX() + Math.max(1, sizeX) - 1,
                    o.getY() + Math.max(1, sizeY) - 1,
                    o.getZ() + Math.max(1, sizeZ) - 1);
        }
    }

    private static final Map<String, Entry> PUBLISHED = new LinkedHashMap<>();
    private static boolean loaded = false;

    private PublishedMaps() {
    }

    // ------------------------------------------------------------------
    // The browsable folder
    // ------------------------------------------------------------------

    /** {@code <world>/aztecabyss} - the folder a server owner actually opens. */
    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("aztecabyss");
    }

    public static Path mapsDir(MinecraftServer server) {
        return root(server).resolve("maps");
    }

    /**
     * Creates the folder and drops a note in it explaining what it is.
     *
     * <p>An empty directory named after a mod tells a server owner nothing. One
     * with a README telling them which file does what, and that editing the JSON
     * is allowed, turns it into something they can use without asking anybody.
     */
    public static void ensureFolder(MinecraftServer server) {
        try {
            Files.createDirectories(mapsDir(server));
            Path readme = root(server).resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Aztec Abyss
                        ===========

                        maps/
                          One <name>.json per published map, and the <name>.nbt it
                          places. The .nbt IS the map: every dealer, price, door cost
                          and spawner setting lives inside it, because sign and marker
                          text travels in structure NBT. Copy that one file to give
                          somebody the whole map.

                          The .json beside it is safe to edit by hand. Change the
                          title, the blurb, the difficulty or which ruleset it plays
                          on, then run /arena reloadmaps in game - no restart.

                          Deleting a pair removes the map from the portal. The blocks
                          stay where they were stamped until something overwrites
                          them; /arena publish <name> puts it back.

                        published.json
                          Which slot each map was stamped into. Written by the game.
                          There is no reason to edit this and good reasons not to.

                        Rulesets live in a datapack, not here:
                          <world>/datapacks/<pack>/data/<you>/abyss_ruleset/<name>.json
                        """, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // A missing folder must not stop a run; publishing will report it.
        }
    }

    // ------------------------------------------------------------------
    // Reading and writing
    // ------------------------------------------------------------------

    public static synchronized void load(MinecraftServer server) {
        PUBLISHED.clear();
        loaded = true;
        ensureFolder(server);
        Path dir = mapsDir(server);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    JsonObject o = JsonParser.parseString(
                            Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                    String name = str(o, "name", p.getFileName().toString().replace(".json", ""));
                    PUBLISHED.put(name.toLowerCase(Locale.ROOT), new Entry(
                            name,
                            str(o, "title", name),
                            str(o, "author", "unknown"),
                            str(o, "blurb", ""),
                            str(o, "difficulty", "MEDIUM"),
                            str(o, "ruleset", "aztecabyss:classic"),
                            num(o, "slot", 0),
                            num(o, "size_x", 64), num(o, "size_y", 32), num(o, "size_z", 64)));
                } catch (Exception ignored) {
                    // One unreadable file should cost one map, not the folder.
                }
            });
        } catch (IOException ignored) {
            // Nothing published yet.
        }
    }

    public static Map<String, Entry> all(MinecraftServer server) {
        if (!loaded) {
            load(server);
        }
        return java.util.Collections.unmodifiableMap(PUBLISHED);
    }

    /** Published maps in slot order - the order they appear on the portal. */
    public static List<Entry> ordered(MinecraftServer server) {
        List<Entry> out = new ArrayList<>(all(server).values());
        out.sort(java.util.Comparator.comparingInt(Entry::slot));
        return out;
    }

    public static Entry byName(MinecraftServer server, String name) {
        return all(server).get(name.toLowerCase(Locale.ROOT));
    }

    /** The lowest slot nobody is using. */
    public static int freeSlot(MinecraftServer server) {
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        for (Entry e : all(server).values()) {
            taken.add(e.slot());
        }
        int slot = 0;
        while (taken.contains(slot)) {
            slot++;
        }
        return slot;
    }

    /** Writes a map's manifest and keeps it in memory. Returns false if it could not. */
    public static boolean save(MinecraftServer server, Entry entry) {
        ensureFolder(server);
        JsonObject o = new JsonObject();
        o.addProperty("name", entry.name());
        o.addProperty("title", entry.title());
        o.addProperty("author", entry.author());
        o.addProperty("blurb", entry.blurb());
        o.addProperty("difficulty", entry.difficulty());
        o.addProperty("ruleset", entry.ruleset());
        o.addProperty("slot", entry.slot());
        o.addProperty("size_x", entry.sizeX());
        o.addProperty("size_y", entry.sizeY());
        o.addProperty("size_z", entry.sizeZ());
        try {
            Files.writeString(mapsDir(server).resolve(entry.name() + ".json"),
                    GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }
        PUBLISHED.put(entry.name().toLowerCase(Locale.ROOT), entry);
        writeIndex(server);
        return true;
    }

    public static boolean remove(MinecraftServer server, String name) {
        Entry gone = PUBLISHED.remove(name.toLowerCase(Locale.ROOT));
        if (gone == null) {
            return false;
        }
        try {
            Files.deleteIfExists(mapsDir(server).resolve(gone.name() + ".json"));
        } catch (IOException ignored) {
            // Gone from the portal either way; the file is cosmetic at this point.
        }
        writeIndex(server);
        return true;
    }

    /** A single file listing what is on the portal and where, for a person to read. */
    private static void writeIndex(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonObject maps = new JsonObject();
        for (Entry e : ordered(server)) {
            JsonObject one = new JsonObject();
            one.addProperty("title", e.title());
            one.addProperty("slot", e.slot());
            one.addProperty("stamped_at_x", e.origin().getX());
            one.addProperty("size", e.sizeX() + " x " + e.sizeY() + " x " + e.sizeZ());
            maps.add(e.name(), one);
        }
        root.add("published", maps);
        try {
            Files.writeString(root(server).resolve("published.json"),
                    GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The index is a convenience; the per-map files are the record.
        }
    }

    /** Copies the saved structure next to its manifest, so one folder holds both. */
    public static void copyStructure(MinecraftServer server, String name) {
        Path from = server.getWorldPath(LevelResource.ROOT)
                .resolve("generated").resolve(MapStore.LOCAL)
                .resolve("structures").resolve(name + ".nbt");
        try {
            if (Files.exists(from)) {
                Files.copy(from, mapsDir(server).resolve(name + ".nbt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // The manifest is what the portal reads; the copy is for the human.
        }
    }

    /**
     * Someone picked a published map off the portal. Send them into it.
     *
     * <p>Joins a run already under way rather than starting a second one. Two
     * people picking the same map ten seconds apart should end up in the same
     * fight - starting a fresh run for the second would silently throw away the
     * first one, which from inside looks like the game deleting your progress
     * because a friend clicked something.
     */
    public static void playFromPicker(net.minecraft.server.level.ServerPlayer player, int index) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        List<Entry> all = ordered(server);
        if (index < 0 || index >= all.size()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cThat map is no longer on the portal."), false);
            return;
        }
        Entry entry = all.get(index);
        net.minecraft.server.level.ServerLevel abyss = server.getLevel(
                com.jrpetty.aztecabyss.AztecAbyssConstants.ABYSS_LEVEL_KEY);
        if (abyss == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cThe Abyss is not loaded."), false);
            return;
        }
        BlockPos origin = entry.origin();
        player.teleportTo(abyss, origin.getX() + entry.sizeX() / 2.0,
                origin.getY() + 2, origin.getZ() + entry.sizeZ() / 2.0,
                java.util.Set.of(), 0.0F, 0.0F);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        if (EngineArena.isRunning()) {
            String joined = EngineArena.joinRun(player);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    joined == null ? "§a✔ Joined the run on §f" + entry.title()
                            : "§c" + joined), false);
            return;
        }
        String error = EngineArena.startIn(abyss, player, entry.bounds(), entry.ruleset());
        if (error == null && EngineArena.active() != null) {
            // Records file under the map, not under the ruleset it happens to use.
            EngineArena.active().setMapKey("custom:" + entry.name());
        }
        if (error != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c" + error), false);
            return;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6§l" + entry.title().toUpperCase(Locale.ROOT)), false);
        if (!entry.blurb().isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7" + entry.blurb()), false);
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
