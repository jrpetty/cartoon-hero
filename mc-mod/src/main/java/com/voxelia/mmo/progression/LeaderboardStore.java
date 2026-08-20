package com.voxelia.mmo.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerPrestige;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's record of everyone who has ever played, so leaderboards can rank
 * offline players too — the online-only view was never a real ranking on a server
 * where people log off.
 *
 * <p>Kept as one small JSON file in the world folder (not a per-player attachment)
 * because it has to be readable for players who aren't here. Written on logout and
 * shutdown, and at most every 30s while people are levelling.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID)
public final class LeaderboardStore {
    private LeaderboardStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final String FILE_NAME = "voxelia_leaderboard.json";
    private static final long SAVE_INTERVAL_MS = 30_000L;

    /** One player's last known standing. Public fields: this is a Gson DTO. */
    public static final class Entry {
        public String name = "";
        public Map<String, Integer> xp = new HashMap<>();
        public Map<String, Integer> prestige = new HashMap<>();
        public long lastSeen;
    }

    /** A ranked row, ready for the screen or the command. */
    public record Row(int rank, String name, int level, int prestige, boolean self) {}

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static Path file;
    private static boolean dirty;
    private static long lastSaveMs;

    // ── lifecycle ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        save(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) record(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            record(player);
            save(true); // a logout is the one moment we're sure their run is over
        }
    }

    /** Snapshots a player's current skills. Cheap — the write is throttled. */
    public static void record(ServerPlayer player) {
        PlayerSkills skills = player.getData(VoxeliaAttachments.PLAYER_SKILLS.get());
        PlayerPrestige prestige = player.getData(VoxeliaAttachments.PLAYER_PRESTIGE.get());

        Entry entry = new Entry();
        entry.name = player.getGameProfile().getName();
        for (Skill s : Skill.values()) entry.xp.put(s.id(), skills.getXp(s));
        entry.prestige = new HashMap<>(prestige.counts());
        entry.lastSeen = System.currentTimeMillis();

        ENTRIES.put(player.getUUID().toString(), entry);
        dirty = true;
        save(false);
    }

    // ── ranking ─────────────────────────────────────────────────────────────

    /** Ranked standings for one skill, or the character average when {@code skill} is null. */
    public static List<Row> top(Skill skill, int limit, UUID viewer) {
        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(ENTRIES.entrySet());
        sorted.sort(Comparator
            .comparingInt((Map.Entry<String, Entry> e) -> -levelOf(e.getValue(), skill))
            .thenComparingInt(e -> -xpOf(e.getValue(), skill))
            .thenComparing(e -> e.getValue().name));

        List<Row> rows = new ArrayList<>();
        String viewerId = viewer == null ? null : viewer.toString();
        for (int i = 0; i < sorted.size() && rows.size() < limit; i++) {
            Map.Entry<String, Entry> e = sorted.get(i);
            rows.add(new Row(i + 1, e.getValue().name, levelOf(e.getValue(), skill),
                prestigeOf(e.getValue(), skill), e.getKey().equals(viewerId)));
        }
        return rows;
    }

    /** Where one player sits in the full ranking, or -1 if they aren't tracked yet. */
    public static int rankOf(UUID player, Skill skill) {
        Entry mine = ENTRIES.get(player.toString());
        if (mine == null) return -1;
        int myLevel = levelOf(mine, skill), myXp = xpOf(mine, skill);
        int better = 0;
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            if (e.getKey().equals(player.toString())) continue;
            int lvl = levelOf(e.getValue(), skill);
            if (lvl > myLevel || (lvl == myLevel && xpOf(e.getValue(), skill) > myXp)) better++;
        }
        return better + 1;
    }

    public static int levelFor(UUID player, Skill skill) {
        Entry e = ENTRIES.get(player.toString());
        return e == null ? 0 : levelOf(e, skill);
    }

    public static int tracked() { return ENTRIES.size(); }

    private static int levelOf(Entry e, Skill skill) {
        if (skill != null) return SkillCurve.levelForXp(e.xp.getOrDefault(skill.id(), 0));
        int total = 0;
        for (Skill s : Skill.values()) total += SkillCurve.levelForXp(e.xp.getOrDefault(s.id(), 0));
        return Math.max(1, Math.round(total / (float) Skill.values().length));
    }

    private static int xpOf(Entry e, Skill skill) {
        if (skill != null) return e.xp.getOrDefault(skill.id(), 0);
        long total = 0;
        for (Skill s : Skill.values()) total += e.xp.getOrDefault(s.id(), 0);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int prestigeOf(Entry e, Skill skill) {
        if (e.prestige == null) return 0;
        if (skill != null) return e.prestige.getOrDefault(skill.id(), 0);
        int total = 0;
        for (Integer v : e.prestige.values()) total += v == null ? 0 : v;
        return total;
    }

    // ── persistence ─────────────────────────────────────────────────────────

    private static void load(MinecraftServer server) {
        ENTRIES.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        dirty = false;
        lastSaveMs = System.currentTimeMillis();
        if (!Files.exists(file)) return;
        try {
            Map<String, Entry> loaded = GSON.fromJson(Files.readString(file), MAP_TYPE);
            if (loaded != null) {
                loaded.forEach((k, v) -> {
                    if (v != null && v.xp != null) ENTRIES.put(k, v);
                });
            }
            VoxeliaMMO.LOGGER.info("Voxelia leaderboard: loaded {} players", ENTRIES.size());
        } catch (Exception e) {
            VoxeliaMMO.LOGGER.warn("Voxelia leaderboard: could not read {}", file, e);
        }
    }

    private static void save(boolean force) {
        if (file == null || !dirty) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastSaveMs < SAVE_INTERVAL_MS) return;
        try {
            Files.writeString(file, GSON.toJson(ENTRIES));
            dirty = false;
            lastSaveMs = now;
        } catch (Exception e) {
            VoxeliaMMO.LOGGER.warn("Voxelia leaderboard: could not write {}", file, e);
        }
    }
}
