package com.jrpetty.aztecabyss.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What a map is, as opposed to what it looks like.
 *
 * <p>Up to now a saved map was a {@code .nbt} file and nothing else - no title, no
 * author, no idea which ruleset it was designed around. That is a structure, not a
 * map. It is loadable by whoever remembers the filename and meaningless to anybody
 * else, which makes it the one thing an engine built for sharing cannot afford.
 *
 * <p>A manifest sits beside the structure and carries the things a person needs to
 * decide whether to play it: what it is called, who made it, what it is like, and
 * which ruleset it expects. It also carries a version, so a map that is revised
 * after being shared can say so.
 *
 * <p>Written as plain JSON next to the structure in the world folder, in exactly
 * the shape a datapack manifest takes. A map made in-game and a map shipped in a
 * datapack are therefore the same object described the same way, and moving one to
 * the other is a copy.
 */
public record MapManifest(String id, String title, String author, String blurb,
                          String difficulty, String ruleset, int version) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static MapManifest fresh(String id, String author) {
        return new MapManifest(id, prettify(id), author,
                "No description yet.", "MEDIUM", "built-in", 1);
    }

    /** Turns "blood_harbour" into "Blood Harbour" for a first-guess title. */
    private static String prettify(String id) {
        String[] parts = id.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }

    public MapManifest with(String field, String value) {
        return switch (field.toLowerCase(java.util.Locale.ROOT)) {
            case "title" -> new MapManifest(id, value, author, blurb, difficulty, ruleset, version);
            case "author" -> new MapManifest(id, title, value, blurb, difficulty, ruleset, version);
            case "blurb" -> new MapManifest(id, title, author, value, difficulty, ruleset, version);
            case "difficulty" -> new MapManifest(id, title, author, blurb,
                    value.toUpperCase(java.util.Locale.ROOT), ruleset, version);
            case "ruleset" -> new MapManifest(id, title, author, blurb, difficulty, value, version);
            default -> this;
        };
    }

    public MapManifest bumped() {
        return new MapManifest(id, title, author, blurb, difficulty, ruleset, version + 1);
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    /** Where in-game maps keep their manifests, beside their structures. */
    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("generated").resolve(MapStore.LOCAL).resolve("arenas");
    }

    public static void save(MinecraftServer server, MapManifest manifest) {
        try {
            Path dir = dir(server);
            Files.createDirectories(dir);
            JsonObject o = new JsonObject();
            o.addProperty("title", manifest.title());
            o.addProperty("author", manifest.author());
            o.addProperty("blurb", manifest.blurb());
            o.addProperty("difficulty", manifest.difficulty());
            o.addProperty("ruleset", manifest.ruleset());
            o.addProperty("version", manifest.version());
            o.addProperty("structure", MapStore.LOCAL + ":" + manifest.id());
            Files.writeString(dir.resolve(manifest.id() + ".json"),
                    GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // A manifest that will not write must not take the map with it - the
            // structure is the irreplaceable half and it is already on disk.
        }
    }

    public static MapManifest load(MinecraftServer server, String id) {
        try {
            Path file = dir(server).resolve(id + ".json");
            if (!Files.isRegularFile(file)) {
                return null;
            }
            JsonObject o = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (o == null) {
                return null;
            }
            return new MapManifest(id,
                    str(o, "title", prettify(id)),
                    str(o, "author", "unknown"),
                    str(o, "blurb", ""),
                    str(o, "difficulty", "MEDIUM"),
                    str(o, "ruleset", "built-in"),
                    o.has("version") ? o.get("version").getAsInt() : 1);
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    /** Every map this world knows about. */
    public static List<MapManifest> listAll(MinecraftServer server) {
        List<MapManifest> out = new ArrayList<>();
        Path dir = dir(server);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString();
                MapManifest m = load(server, name.substring(0, name.length() - 5));
                if (m != null) {
                    out.add(m);
                }
            });
        } catch (IOException ignored) {
            // An unreadable directory is an empty list, not a crash.
        }
        out.sort(java.util.Comparator.comparing(MapManifest::title));
        return out;
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** A one-line summary for a list. */
    public String line() {
        return "§f" + title + " §8v" + version + " §7by " + author
                + " §8— " + difficulty + ", ruleset §7" + ruleset;
    }
}
