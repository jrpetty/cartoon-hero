package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One instruction, written on a sign, somewhere in a build.
 *
 * <p>This is the whole authoring model: an author never types a coordinate. They
 * stand in the room, place a sign, and write what the place <em>is</em>. The engine
 * reads the world back and works out the geometry itself.
 *
 * <pre>
 *   [Horde]
 *   area=hall
 *   depth=6
 * </pre>
 *
 * <p>Line 0 is the kind. Lines 1-3 are free-form {@code key=value} pairs, so new
 * options never need a new line layout - and an argument a given kind does not
 * understand is simply ignored rather than being an error. That is deliberate: a
 * map written for a later version of the engine should still load on an earlier
 * one, minus the features it did not know about.
 *
 * <p>Facing comes from the sign itself. A wall sign already points somewhere, and
 * making the author state a direction they can see would be asking them to repeat
 * themselves.
 */
public record Marker(String kind, BlockPos pos, Direction facing, Map<String, String> args) {

    /**
     * This marker's identity, lower-cased.
     *
     * <p>Six places ask "which region is this" on a tick - the region sweep, the
     * zone rules, {@code regionAt}, {@code route}, {@code power} and the script's
     * lookups - and each was calling {@code arg("id", ...)} then
     * {@code toLowerCase()} per marker, per player, several times a second, for a
     * value fixed when the map was stamped.
     *
     * <p>Not cached on the marker itself: a record hashes every component, args
     * map included, so keying a cache on one costs more than the lower-casing it
     * would save. The callers that loop hold the lower-cased ids beside the list
     * instead, computed once when the map is scanned.
     */
    public String id() {
        return arg("id", arg("value", "")).toLowerCase(java.util.Locale.ROOT);
    }

    public String arg(String key, String fallback) {
        return args.getOrDefault(key, fallback);
    }

    /**
     * A numeric argument, or the fallback if it is absent or unreadable.
     *
     * <p>The absent case returns without touching the parser. It used to go
     * through {@code getOrDefault(key, String.valueOf(fallback))}, and Java
     * evaluates that second argument whether the key is there or not - so every
     * ask for an option a sign did not set built a String, trimmed it and parsed
     * it back to the number it started as. This is the most-called accessor in
     * the engine: the region sweep, the zone sweep, the traps and the objective
     * all read radii off markers several times a second, and nearly every read
     * is a miss, because the defaults are the point of having defaults.
     */
    public int intArg(String key, int fallback) {
        String raw = args.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Reads a sign as a marker, or returns null if it is not one.
     *
     * <p>Anything malformed yields null rather than throwing. One bad sign in a
     * large map should make that sign inert and nothing else.
     */
    public static Marker parse(SignBlockEntity sign, BlockState state) {
        String head = text(sign, 0).toLowerCase(Locale.ROOT);
        if (head.length() < 3 || !head.startsWith("[") || !head.endsWith("]")) {
            return null;
        }
        String kind = head.substring(1, head.length() - 1).trim();
        if (kind.isEmpty()) {
            return null;
        }
        Map<String, String> args = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            for (String token : text(sign, i).split("\\s+")) {
                int eq = token.indexOf('=');
                if (eq > 0 && eq < token.length() - 1) {
                    args.put(token.substring(0, eq).toLowerCase(Locale.ROOT),
                            token.substring(eq + 1));
                } else if (!token.isBlank() && !args.containsKey("value")) {
                    // A bare word is kept as "value", so [Perk] ironhide works
                    // as well as [Perk] id=ironhide.
                    args.put("value", token);
                }
            }
        }
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        return new Marker(kind, sign.getBlockPos().immutable(), facing, args);
    }

    /**
     * Reads a Marker Block as a marker.
     *
     * <p>The same grammar as a sign, over eight long lines instead of four short
     * ones. Deliberately a second entry point into one parser rather than a second
     * parser: an author who learned markers on signs already knows this, and a map
     * mixing the two reads identically to the engine.
     */
    public static Marker parse(com.jrpetty.aztecabyss.block.MarkerBlockEntity block, BlockState state) {
        String kind = block.kind().toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) {
            return null;
        }
        Map<String, String> args = new HashMap<>();
        java.util.List<String> lines = block.lines();
        for (int i = 1; i < lines.size(); i++) {
            absorb(lines.get(i), args);
        }
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        return new Marker(kind, block.getBlockPos().immutable(), facing, args);
    }

    /** One line of {@code key=value} tokens into the argument map. */
    private static void absorb(String line, Map<String, String> args) {
        for (String token : line.trim().split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq > 0 && eq < token.length() - 1) {
                args.put(token.substring(0, eq).toLowerCase(Locale.ROOT), token.substring(eq + 1));
            } else if (!token.isBlank() && !args.containsKey("value")) {
                args.put("value", token);
            }
        }
    }

    private static String text(SignBlockEntity sign, int index) {
        Component c = sign.getFrontText().getMessage(index, false);
        return c.getString().trim();
    }

    @Override
    public String toString() {
        return "[" + kind + "] at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + (args.isEmpty() ? "" : " " + args);
    }
}
