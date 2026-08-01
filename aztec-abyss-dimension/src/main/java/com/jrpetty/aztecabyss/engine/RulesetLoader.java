package com.jrpetty.aztecabyss.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
 * Loads rulesets out of datapacks, and reloads them without restarting.
 *
 * <p>Datapacks are the delivery mechanism for the whole engine, and this is the
 * first piece to actually use them. It matters that this is a reload listener and
 * not a one-off read at startup: tuning a survival mode is a loop of change a
 * number, play a round, change it again, and a loop with a server restart in it is
 * a loop nobody runs more than twice. {@code /reload} and the new numbers are live.
 *
 * <p>Files live at {@code data/<namespace>/abyss_ruleset/<name>.json}. The
 * namespace is the author's, so two people can both ship a ruleset called
 * {@code hard} and neither shadows the other.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class RulesetLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final Map<String, Ruleset> LOADED = new LinkedHashMap<>();

    public RulesetLoader() {
        super(GSON, "abyss_ruleset");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files,
                         ResourceManager manager, ProfilerFiller profiler) {
        LOADED.clear();
        // Currencies are declared by rulesets, so they are rebuilt from scratch on
        // every reload - otherwise a currency deleted from a file would linger in
        // memory and keep working, which is the worst kind of stale.
        Currency.clearCustom();

        files.forEach((id, element) -> {
            if (!element.isJsonObject()) {
                return;
            }
            String key = id.toString();
            Ruleset rules = Ruleset.parse(key, element.getAsJsonObject());
            LOADED.put(key, rules);
            readCurrencies(element.getAsJsonObject());
        });
    }

    /** Pulls any currency declarations out of a ruleset file. */
    private static void readCurrencies(com.google.gson.JsonObject root) {
        if (!root.has("currencies") || !root.get("currencies").isJsonArray()) {
            return;
        }
        for (JsonElement el : root.getAsJsonArray("currencies")) {
            if (!el.isJsonObject()) {
                continue;
            }
            com.google.gson.JsonObject c = el.getAsJsonObject();
            String cid = get(c, "id", "");
            if (cid.isEmpty() || cid.equals("points")) {
                continue; // the stock currency is not redefinable
            }
            String name = get(c, "name", cid);
            String symbol = get(c, "symbol", "✦");
            String colour = get(c, "colour", "§f");
            String backing = get(c, "backing", "virtual").toLowerCase(java.util.Locale.ROOT);
            switch (backing) {
                case "item" -> Currency.register(Currency.backedByItem(
                        cid, name, symbol, colour, get(c, "item", "minecraft:gold_ingot")));
                case "experience" -> Currency.register(Currency.experience(cid, name, symbol, colour));
                default -> {
                    int start = 0;
                    try {
                        start = c.has("start") ? c.get("start").getAsInt() : 0;
                    } catch (RuntimeException ignored) {
                        start = 0;
                    }
                    Currency.register(Currency.virtual(cid, name, symbol, colour, start));
                }
            }
        }
    }

    private static String get(com.google.gson.JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public static Ruleset byId(String id) {
        Ruleset r = LOADED.get(id);
        return r != null ? r : Ruleset.defaults("built-in");
    }

    public static Map<String, Ruleset> all() {
        return java.util.Collections.unmodifiableMap(LOADED);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new RulesetLoader());
    }
}
