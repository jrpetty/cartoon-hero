package com.jrpetty.mobtrumps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The server's ledger of how many cards of each mob it has ever issued.
 *
 * <p>One counter per mob id, so every mob has exactly one 000001 — its first
 * card on this server, forever. Counters only ever go up: destroying a card
 * does not hand its number back, which is the whole point of a low serial
 * meaning something.
 *
 * <p>Stored as world save data on the overworld, so it survives restarts and
 * crashes, and keyed by mob id string rather than an enum so cards from modded
 * mobs register themselves without anything being hard-coded.
 */
public final class SerialRegistry extends SavedData {

    private static final String FILE = "mobtrumps_serials";

    private final Map<String, Integer> issued = new HashMap<>();

    public SerialRegistry() {
    }

    private SerialRegistry(CompoundTag tag) {
        for (String key : tag.getAllKeys()) {
            issued.put(key, tag.getInt(key));
        }
    }

    public static SavedData.Factory<SerialRegistry> factory() {
        return new SavedData.Factory<>(SerialRegistry::new,
                (tag, registries) -> new SerialRegistry(tag), null);
    }

    public static SerialRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE);
    }

    /**
     * Claim the next serial for a mob. Synchronised so two kills landing in the
     * same tick — or on different threads — can never be handed the same number.
     */
    public synchronized int next(String mobId) {
        int n = issued.getOrDefault(mobId, 0) + 1;
        issued.put(mobId, n);
        setDirty();
        return n;
    }

    /** How many cards of this mob have ever been issued. */
    public synchronized int count(String mobId) {
        return issued.getOrDefault(mobId, 0);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        synchronized (this) {
            issued.forEach(tag::putInt);
        }
        return tag;
    }
}
