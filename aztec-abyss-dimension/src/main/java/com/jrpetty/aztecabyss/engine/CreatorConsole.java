package com.jrpetty.aztecabyss.engine;

import com.jrpetty.aztecabyss.network.CreatorConsolePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The Creator Console: everything the Map Creator does, on a screen.
 *
 * <h2>Why</h2>
 *
 * <p>The authoring tools were all {@code /arena} commands - and every one of
 * them is operator-only. The password that lets somebody into the Workshop
 * therefore let them build and nothing else: they could not check a map, play
 * it, save it or put it on the portal, and they could not even leave. The mode
 * on the picker that said "build your own" was, for anybody but an operator, a
 * flat world with no way out.
 *
 * <p>Right-clicking the air with the Map Wand opens this instead. It runs the
 * same code the commands run - one implementation, so a map saved from the
 * console and a map saved by command are the same file - gated on being in the
 * Workshop and being allowed in, not on being an operator.
 *
 * <h2>What it keeps</h2>
 *
 * <p>Only the last check's findings and the last status line per player, so the
 * console can show the answer to the button that was just pressed. Everything
 * else - the selection, the saved maps, what is on the portal - is read fresh
 * every time the console is sent, so it can never disagree with the world.
 */
public final class CreatorConsole {

    private static final Map<UUID, List<String>> PROBLEMS = new HashMap<>();
    private static final Map<UUID, String> STATUS = new HashMap<>();

    /** What a map may be called: what the save format and the commands accept. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private static final List<String> DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD", "BRUTAL");

    /** The ruleset a map publishes with when it names none of its own. */
    private static final String STOCK = "aztecabyss:classic";

    private CreatorConsole() {
    }

    /** Opens the console, or refreshes it, for somebody allowed to use it. */
    public static void open(ServerPlayer player) {
        if (!MapCreator.inWorkshop(player) || !MapCreator.mayEnter(player)) {
            return;
        }
        send(player);
    }

    /** One button on the console. Every check the commands make is made again. */
    public static void onAction(ServerPlayer player, String action, String a, String b) {
        MinecraftServer server = player.getServer();
        if (server == null || !MapCreator.inWorkshop(player) || !MapCreator.mayEnter(player)) {
            return;
        }
        UUID id = player.getUUID();
        switch (action) {
            case "check" -> check(player);
            case "test" -> {
                String rules = a.isBlank() ? "built-in" : a;
                if (EngineEvents.consoleTest(player, rules)) {
                    STATUS.put(id, "§aTesting on " + RulesetLoader.byId(rules).displayTitle()
                            + ". §7Right-click the air with the wand to stop.");
                    return; // the screen closes itself so the test can be played
                }
                STATUS.put(id, "§cThe test did not start — the reason is in chat.");
            }
            case "stop" -> {
                EngineArena run = EngineArena.active();
                if (run != null && run.level() == player.level()) {
                    EngineArena.stop(true);
                }
                player.setGameMode(GameType.CREATIVE);
                STATUS.put(id, "§7Test over. Back to building.");
            }
            case "save" -> {
                String name = a.trim().toLowerCase(Locale.ROOT);
                if (!NAME.matcher(name).matches()) {
                    STATUS.put(id, "§cA map name is lowercase letters, digits, - and _, up to 32.");
                } else if (BuildTools.selectionOf(player) == null) {
                    STATUS.put(id, "§cMark the map out with the wand first.");
                } else {
                    STATUS.put(id, EngineEvents.consoleCreate(player, name)
                            ? "§aSaved §f" + name + "§a. §7Give it a title, then publish it."
                            : "§cCould not save it — the reason is in chat.");
                }
            }
            case "meta" -> STATUS.put(id, editDetail(server, a, b));
            case "publish" -> STATUS.put(id, MapManifest.load(server, a) == null
                    ? "§cThere is no saved map called " + a + "."
                    : EngineEvents.consolePublish(player, a)
                            ? "§a" + a + " is on the portal. §7Anybody can pick it under Player Maps."
                            : "§cCould not publish it — the reason is in chat.");
            case "unpublish" -> STATUS.put(id, PublishedMaps.remove(server, a)
                    ? "§7" + a + " is off the portal. §8The blocks are still where they were."
                    : "§cIt was not on the portal.");
            case "marker" -> {
                String kind = a.toLowerCase(Locale.ROOT);
                if (List.of(BuildTools.KINDS).contains(kind)) {
                    // A sign, not a marker block: a sign can be re-read and
                    // re-written by hand, and the blocks' extra lines are only
                    // writable by command - which is the thing this console
                    // exists to make unnecessary.
                    give(player, BuildTools.markerSign(kind, BuildTools.hintFor(kind)));
                    STATUS.put(id, "§6[" + kind + "] §7sign in your hand — place it, and edit its "
                            + "lines by hand if the map needs them different.");
                }
            }
            case "wand" -> {
                give(player, BuildTools.wand());
                STATUS.put(id, "§6A fresh Map Wand. §7Left-click one corner, right-click the other.");
            }
            case "leave" -> {
                MapCreator.leave(player);
                PROBLEMS.remove(id);
                STATUS.remove(id);
                return;
            }
            default -> {
                // "refresh", or anything this server does not know: just resend.
            }
        }
        send(player);
    }

    /** Runs the validator over the wand's box and keeps what it found. */
    private static void check(ServerPlayer player) {
        UUID id = player.getUUID();
        var box = BuildTools.selectionOf(player);
        if (box == null) {
            PROBLEMS.remove(id);
            STATUS.put(id, "§cMark the map out with the wand first: left-click one corner, right-click the other.");
            return;
        }
        if (BuildTools.volumeOf(box) > 8_000_000L) {
            PROBLEMS.remove(id);
            STATUS.put(id, "§cThat box is " + BuildTools.volumeOf(box) + " blocks. Keep it under 8,000,000.");
            return;
        }
        MapScan.Result scan = MapScan.scan((ServerLevel) player.level(), box);
        List<String> problems = MapScan.validate(scan);
        PROBLEMS.put(id, problems);
        STATUS.put(id, problems.isEmpty()
                ? "§a✔ Playable — " + scan.all().size() + (scan.all().size() == 1 ? " marker, " : " markers, ")
                        + scan.count("horde") + (scan.count("horde") == 1 ? " way in." : " ways in.")
                : "§6" + problems.size() + (problems.size() == 1 ? " thing" : " things") + " to fix:");
    }

    /**
     * Sets one detail of a saved map. Only the four a player writes; each is
     * cleaned of the characters the packing and the chat formatting use, and
     * kept to a length a card can show.
     */
    private static String editDetail(MinecraftServer server, String name, String pair) {
        MapManifest m = MapManifest.load(server, name);
        if (m == null) {
            return "§cThere is no saved map called " + name + ".";
        }
        int eq = pair.indexOf('=');
        String field = eq > 0 ? pair.substring(0, eq) : "";
        String value = eq > 0 ? pair.substring(eq + 1).replace('|', '/').replaceAll("§.?", "").trim() : "";
        switch (field) {
            case "title" -> value = value.length() > 40 ? value.substring(0, 40) : value;
            case "blurb" -> value = value.length() > 160 ? value.substring(0, 160) : value;
            case "difficulty" -> {
                value = value.toUpperCase(Locale.ROOT);
                if (!DIFFICULTIES.contains(value)) {
                    return "§cDifficulty is one of " + String.join(", ", DIFFICULTIES) + ".";
                }
            }
            case "ruleset" -> {
                if (!value.equals("built-in") && !RulesetLoader.all().containsKey(value)) {
                    return "§cNo ruleset called " + value + ".";
                }
            }
            default -> {
                return "§cThat is not a detail a map has.";
            }
        }
        if (value.isEmpty()) {
            return "§cIt cannot be blank.";
        }
        MapManifest updated = m.with(field, value).bumped();
        MapManifest.save(server, updated);
        boolean live = PublishedMaps.byName(server, name) != null;
        return "§a" + field + " saved §8(v" + updated.version() + ")"
                + (live ? " §7— publish again to update the portal." : "");
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Builds the console's view of the world for this player and sends it. */
    static void send(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID id = player.getUUID();
        var box = BuildTools.selectionOf(player);
        String selection = box == null ? "" : BuildTools.spanText(box) + "|" + BuildTools.volumeOf(box);

        // "built-in" is what a fresh map says, and publishing turns it into the
        // stock ruleset - so the console calls it by that ruleset's name, and a
        // play-test runs exactly what the portal will. It used to read "Built-in
        // rules" here and "Classic Hold" on the portal, for the same map.
        boolean stock = RulesetLoader.all().containsKey(STOCK);
        List<String> maps = new ArrayList<>();
        for (MapManifest m : MapManifest.listAll(server)) {
            boolean live = PublishedMaps.byName(server, m.id()) != null;
            String rules = stock && "built-in".equals(m.ruleset()) ? STOCK : m.ruleset();
            maps.add(m.id() + "|" + clean(m.title()) + "|" + m.version() + "|" + clean(m.difficulty())
                    + "|" + clean(rules) + "|" + clean(m.blurb()) + "|" + (live ? 1 : 0));
        }
        List<String> rulesets = new ArrayList<>();
        if (stock) {
            rulesets.add(STOCK + "|" + clean(RulesetLoader.byId(STOCK).displayTitle()));
        } else {
            rulesets.add("built-in|Built-in rules");
        }
        RulesetLoader.all().forEach((rid, r) -> {
            if (!rid.equals(STOCK)) {
                rulesets.add(rid + "|" + clean(r.displayTitle()));
            }
        });

        EngineArena run = EngineArena.active();
        boolean testing = run != null && EngineArena.isRunning() && run.level() == player.level()
                && run.isParticipant(player);
        int flags = (testing ? CreatorConsolePayload.FLAG_TESTING : 0)
                | (player.hasPermissions(2) ? CreatorConsolePayload.FLAG_OPERATOR : 0);
        PacketDistributor.sendToPlayer(player, new CreatorConsolePayload(selection,
                STATUS.getOrDefault(id, ""), PROBLEMS.getOrDefault(id, List.of()), maps, rulesets, flags));
    }

    /** Strips the separator the payload packs with. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/');
    }

    /** Forgets a player's console state, for when they log out. */
    public static void forget(UUID id) {
        PROBLEMS.remove(id);
        STATUS.remove(id);
    }
}
