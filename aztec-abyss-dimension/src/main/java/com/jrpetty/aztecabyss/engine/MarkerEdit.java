package com.jrpetty.aztecabyss.engine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Changing one property of a marker without retyping the sign.
 *
 * <p>The authoring loop had a hole in the middle of it: tuning. Deciding a crossbow
 * should cost 1400 rather than 1750 meant breaking the sign, placing a new one, and
 * typing all four lines again - for a three-character change. Do that thirty times
 * across a map and the tool is fighting you. Every editor worth using lets you
 * select a thing and change one field of it, which is what this is.
 *
 * <pre>
 *   /arena set price 1400
 *   /arena set area cellar
 *   /arena set item minecraft:diamond_sword
 * </pre>
 *
 * <p>It edits whatever marker you are looking at. That is the whole interface -
 * there is no selection state to get out of sync with the world, and pointing at
 * the thing you mean is the least ambiguous way anyone has found to say which one.
 *
 * <p>Rewriting the sign is done by rebuilding its text from the parsed marker
 * rather than by patching the line the value happened to be on. Arguments are
 * free-form across three lines, so "the price" has no fixed home - and a rewrite
 * that assumed one would quietly corrupt any sign whose author laid it out
 * differently.
 */
public final class MarkerEdit {

    /** How far to look for a sign. Roughly reach distance in creative. */
    private static final double REACH = 6.0;

    private MarkerEdit() {
    }

    /** The sign a player is looking at, or null. */
    public static SignBlockEntity targeted(ServerLevel level, ServerPlayer player) {
        var hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) {
            return null;
        }
        return level.getBlockEntity(block.getBlockPos()) instanceof SignBlockEntity sign ? sign : null;
    }

    /**
     * Sets one key on the targeted marker.
     *
     * @return a message describing what happened, never null
     */
    public static String set(ServerLevel level, ServerPlayer player, String key, String value) {
        SignBlockEntity sign = targeted(level, player);
        if (sign == null) {
            return "§cLook at a marker sign first.";
        }
        Marker marker = Marker.parse(sign, level.getBlockState(sign.getBlockPos()));
        if (marker == null) {
            return "§cThat is not a marker — its first line needs a kind in brackets.";
        }
        String k = key.toLowerCase(Locale.ROOT);

        // Dealer signs are positional by design - item on line 2, price on line 3 -
        // so they get handled as the shaped thing they are rather than as loose
        // key=value pairs, which is what a dealer's lines would otherwise become.
        if (marker.kind().equals("dealer")) {
            return setOnDealer(level, sign, k, value);
        }

        Map<String, String> args = new LinkedHashMap<>(marker.args());
        if (value.equalsIgnoreCase("-") || value.isEmpty()) {
            args.remove(k);
        } else {
            args.put(k, value);
        }
        write(level, sign, "[" + marker.kind() + "]", argLines(args));
        return "§a✔ " + marker.kind() + " · §f" + k + " §7→ §f" + value;
    }

    private static String setOnDealer(ServerLevel level, SignBlockEntity sign,
                                      String key, String value) {
        String item = line(sign, 1);
        String price = line(sign, 2);
        String extra = line(sign, 3);
        switch (key) {
            case "item" -> item = value;
            case "price", "cost" -> {
                // Keep any currency already named on the price line.
                String[] parts = price.trim().split("\\s+");
                price = parts.length > 1 ? value + " " + parts[1] : value;
            }
            case "currency" -> {
                String[] parts = price.trim().split("\\s+");
                price = (parts.length > 0 ? parts[0] : "0") + " " + value;
            }
            case "count" -> extra = "x" + value;
            default -> {
                return "§7A dealer understands §fitem§7, §fprice§7, §fcurrency§7 and §fcount§7.";
            }
        }
        write(level, sign, "[Dealer]", new String[]{item, price, extra});
        return "§a✔ dealer · §f" + key + " §7→ §f" + value;
    }

    /** Packs arguments back onto three lines without splitting a pair across two. */
    private static String[] argLines(Map<String, String> args) {
        StringBuilder[] lines = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
        int at = 0;
        for (Map.Entry<String, String> e : args.entrySet()) {
            String token = e.getKey().equals("value")
                    ? e.getValue()
                    : e.getKey() + "=" + e.getValue();
            // 15 characters is about what fits on a sign line at default width.
            if (lines[at].length() > 0 && lines[at].length() + token.length() + 1 > 15) {
                at = Math.min(2, at + 1);
            }
            if (lines[at].length() > 0) {
                lines[at].append(' ');
            }
            lines[at].append(token);
        }
        return new String[]{lines[0].toString(), lines[1].toString(), lines[2].toString()};
    }

    private static String line(SignBlockEntity sign, int index) {
        return sign.getFrontText().getMessage(index, false).getString().trim();
    }

    /**
     * Replaces a sign's front text.
     *
     * <p>Goes through the sign's own {@code SignText}, which is immutable - each
     * set returns a new one - so the four lines are built up and handed over once.
     * Then an explicit block update, because a block entity changing on the server
     * does not on its own tell anybody's client to redraw it, and a tuning tool
     * whose changes only appear after a relog is worse than no tool.
     */
    private static void write(ServerLevel level, SignBlockEntity sign, String head, String[] rest) {
        net.minecraft.world.level.block.entity.SignText text = sign.getFrontText();
        text = text.setMessage(0, Component.literal(head));
        for (int i = 0; i < 3; i++) {
            text = text.setMessage(i + 1,
                    Component.literal(i < rest.length ? rest[i] : ""));
        }
        sign.setText(text, true);
        sign.setChanged();
        var state = level.getBlockState(sign.getBlockPos());
        level.sendBlockUpdated(sign.getBlockPos(), state, state, 3);
    }

    /** Reads back what a marker currently says, for the inspector. */
    public static String describe(ServerLevel level, ServerPlayer player) {
        SignBlockEntity sign = targeted(level, player);
        if (sign == null) {
            return "§7Look at a marker sign.";
        }
        Marker marker = Marker.parse(sign, level.getBlockState(sign.getBlockPos()));
        if (marker == null) {
            return "§7That sign is not a marker.";
        }
        StringBuilder sb = new StringBuilder("§6[" + marker.kind() + "]§7 facing "
                + marker.facing().getName());
        marker.args().forEach((k, v) -> sb.append(" §8| §f").append(k).append("§7=").append(v));
        return sb.toString();
    }
}
