package com.jrpetty.aztecabyss.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps that arrive in a datapack.
 *
 * <p>This closes a gap I had already claimed was closed. Structure {@code .nbt}
 * files load from datapacks because vanilla loads them - that part always worked -
 * but manifests were only ever read from the world folder. So a shared map arrived
 * as geometry with no title, no author and no idea which ruleset it was designed
 * around, and the recipient had to be told all of that out of band.
 *
 * <p>Sharing half a map is worse than not claiming to share at all, because the
 * missing half is the part that makes a file into something a person can choose.
 *
 * <p>Files live at {@code data/<namespace>/abyss_arena/<name>.json} and name the
 * structure they belong to:
 *
 * <pre>
 * {
 *   "title": "Blood Harbour",
 *   "author": "someone",
 *   "blurb": "A flooded dock. The tide decides where you can stand.",
 *   "difficulty": "HARD",
 *   "ruleset": "bloodharbour:harbour_endless",
 *   "structure": "bloodharbour:blood_harbour"
 * }
 * </pre>
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class ArenaLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    /** Manifest by its full id, e.g. {@code bloodharbour:blood_harbour}. */
    private static final Map<String, MapManifest> LOADED = new LinkedHashMap<>();
    /** Which structure each one places. */
    private static final Map<String, String> STRUCTURES = new LinkedHashMap<>();

    public ArenaLoader() {
        super(GSON, "abyss_arena");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        LOADED.clear();
        STRUCTURES.clear();
        files.forEach((id, element) -> {
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject o = element.getAsJsonObject();
            String key = id.toString();
            LOADED.put(key, new MapManifest(key,
                    str(o, "title", id.getPath()),
                    str(o, "author", "unknown"),
                    str(o, "blurb", ""),
                    str(o, "difficulty", "MEDIUM"),
                    str(o, "ruleset", "built-in"),
                    intOf(o, "version", 1)));
            // A manifest that names no structure defaults to one sharing its own
            // id, which is what an author would have called it anyway.
            STRUCTURES.put(key, str(o, "structure", key));
        });
    }

    public static Map<String, MapManifest> all() {
        return java.util.Collections.unmodifiableMap(LOADED);
    }

    public static MapManifest byId(String id) {
        return LOADED.get(id);
    }

    /**
     * The structure a datapack map places, or null if no such map is loaded.
     *
     * <p>Accepts a bare name as well as a full id, because someone reading a map
     * out of a list will type the short version and being strict about namespaces
     * would only be right and unhelpful.
     */
    public static String structureFor(String name) {
        String direct = STRUCTURES.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> e : STRUCTURES.entrySet()) {
            String path = e.getKey().substring(e.getKey().indexOf(':') + 1);
            if (path.equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** The manifest for a bare or full name. */
    public static MapManifest manifestFor(String name) {
        MapManifest direct = LOADED.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MapManifest> e : LOADED.entrySet()) {
            String path = e.getKey().substring(e.getKey().indexOf(':') + 1);
            if (path.equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArenaLoader());
    }
}
