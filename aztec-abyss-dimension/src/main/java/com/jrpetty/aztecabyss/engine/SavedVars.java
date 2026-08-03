package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What the <em>world</em> remembers, as opposed to what a run does.
 *
 * <p>{@link Vars} opens by describing itself as "what a run remembers", and that
 * turned out to be the whole limitation. Every number the engine could hold died
 * the moment the run ended, so nothing that happened in a map could ever matter
 * to the next one. That rules out an entire category of game rather than an
 * entire genre:
 *
 * <ul>
 *   <li>a <b>campaign</b> - beat this map, unlock the next</li>
 *   <li><b>progression</b> - you have escaped three times, here is a better kit</li>
 *   <li>a <b>persistent world</b> - the first squad to crack the vault opens it
 *       for everybody who comes after</li>
 *   <li>a <b>profile</b> - anything at all that is true about a player rather
 *       than about their current attempt</li>
 * </ul>
 *
 * <p>None of those are hard. They were impossible, because there was nowhere to
 * put the number.
 *
 * <h2>Namespaced by ruleset, unless you ask otherwise</h2>
 *
 * <p>A saved variable belongs to the map that wrote it. Two maps that both count
 * {@code score} do not fight, which matters far more here than it does for run
 * variables: a run's names vanish in twenty minutes and these do not. An author
 * who genuinely wants the shared namespace - which is what a campaign spanning
 * several maps needs - asks for it with {@code "global": true}, and has thereby
 * said out loud that they intend to share.
 *
 * <h2>Bounded, because this one is written to disk</h2>
 *
 * <p>Run variables are capped to stop a map hanging a server. These are capped
 * for a different reason: they persist. An unbounded persistent store is a map
 * that can grow your save file forever, and a downloaded map should not be able
 * to do that any more than it should be able to run a program.
 */
public final class SavedVars extends SavedData {

    public static final String NAME = "aztecabyss_engine_saved";

    /** Distinct names the whole world may keep. */
    private static final int MAX_WORLD_KEYS = 512;
    /** Distinct names one player may keep. */
    private static final int MAX_PLAYER_KEYS = 128;

    private final Map<String, Integer> world = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Integer>> player = new HashMap<>();

    /**
     * Builds the stored name.
     *
     * <p>The ruleset prefix is the whole of the collision safety, so it is applied
     * here rather than at every call site - a namespace that each caller has to
     * remember to apply is a namespace that leaks.
     */
    private static String key(String rulesetId, String raw, boolean global) {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (global || rulesetId == null || rulesetId.isEmpty()) {
            return name;
        }
        return rulesetId.toLowerCase(Locale.ROOT) + "/" + name;
    }

    // ------------------------------------------------------------------
    // World scope
    // ------------------------------------------------------------------

    public int get(String rulesetId, String name, boolean global) {
        return world.getOrDefault(key(rulesetId, name, global), 0);
    }

    public void set(String rulesetId, String name, int value, boolean global) {
        String k = key(rulesetId, name, global);
        if (k.isEmpty() || (!world.containsKey(k) && world.size() >= MAX_WORLD_KEYS)) {
            return;
        }
        world.put(k, value);
        setDirty();
    }

    public void add(String rulesetId, String name, int delta, boolean global) {
        set(rulesetId, name, get(rulesetId, name, global) + delta, global);
    }

    // ------------------------------------------------------------------
    // Player scope
    // ------------------------------------------------------------------

    public int get(UUID who, String rulesetId, String name, boolean global) {
        Map<String, Integer> mine = player.get(who);
        return mine == null ? 0 : mine.getOrDefault(key(rulesetId, name, global), 0);
    }

    public void set(UUID who, String rulesetId, String name, int value, boolean global) {
        String k = key(rulesetId, name, global);
        if (k.isEmpty() || who == null) {
            return;
        }
        Map<String, Integer> mine = player.computeIfAbsent(who, u -> new LinkedHashMap<>());
        if (!mine.containsKey(k) && mine.size() >= MAX_PLAYER_KEYS) {
            return;
        }
        mine.put(k, value);
        setDirty();
    }

    public void add(UUID who, String rulesetId, String name, int delta, boolean global) {
        set(who, rulesetId, name, get(who, rulesetId, name, global) + delta, global);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag w = new CompoundTag();
        world.forEach(w::putInt);
        tag.put("World", w);
        CompoundTag people = new CompoundTag();
        player.forEach((id, mine) -> {
            CompoundTag one = new CompoundTag();
            mine.forEach(one::putInt);
            people.put(id.toString(), one);
        });
        tag.put("Players", people);
        return tag;
    }

    public static SavedVars load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedVars out = new SavedVars();
        CompoundTag w = tag.getCompound("World");
        for (String k : w.getAllKeys()) {
            out.world.put(k, w.getInt(k));
        }
        CompoundTag people = tag.getCompound("Players");
        for (String id : people.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(id);
                CompoundTag one = people.getCompound(id);
                Map<String, Integer> mine = new LinkedHashMap<>();
                for (String k : one.getAllKeys()) {
                    mine.put(k, one.getInt(k));
                }
                out.player.put(uuid, mine);
            } catch (IllegalArgumentException ignored) {
                // Not a uuid. Not worth losing everybody else's progress over.
            }
        }
        return out;
    }

    public static SavedData.Factory<SavedVars> factory() {
        return new SavedData.Factory<>(SavedVars::new, SavedVars::load, null);
    }

    public static SavedVars get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static SavedVars get(ServerLevel level) {
        return get(level.getServer());
    }
}
