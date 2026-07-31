package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs and records for the maze.
 *
 * <p>A run is the clock from leaving the Glade to reaching the day's exit. It is
 * per-player, not per-session: two people in the maze are on two clocks, and
 * either can be mid-run while the other is not.
 *
 * <p>Dying does not end a run. It costs you thirty seconds - the handoff's
 * default - and puts you back in the Glade. That is the more interesting rule:
 * an escape is still an escape, but a sloppy one is worth less, so a runner who
 * has died twice has a real reason to keep going rather than reset.
 *
 * <p>Only the ten fastest escapes are kept, and they persist with the world.
 */
public final class MazeRuns extends SavedData {

    public static final String NAME = "aztecabyss_maze_runs";
    private static final int TOP_N = 10;

    /** A completed escape. */
    public record Record(String name, int seconds, int deaths, int day, String layout) {
    }

    /** A run in progress. */
    private record Live(long startedTick, int deaths, boolean leftGlade) {
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    private final List<Record> records = new ArrayList<>();

    public MazeRuns() {
    }

    // ------------------------------------------------------------------
    // Live runs
    // ------------------------------------------------------------------

    public static boolean isRunning(UUID id) {
        return LIVE.containsKey(id);
    }

    /** Starts the clock. Called the moment a runner steps out of the Glade. */
    public static void begin(ServerLevel level, ServerPlayer player) {
        if (LIVE.containsKey(player.getUUID())) {
            return;
        }
        LIVE.put(player.getUUID(), new Live(level.getGameTime(), 0, true));
        player.displayClientMessage(Component.literal(
                "§a▶ Run started. §7Find the way out before the doors seal."), true);
    }

    public static void abandon(UUID id) {
        LIVE.remove(id);
    }

    public static void clearAll() {
        LIVE.clear();
    }

    /** Adds the death penalty and keeps the clock running. */
    public static void onDeath(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        if (live == null) {
            return;
        }
        LIVE.put(player.getUUID(), new Live(live.startedTick(), live.deaths() + 1, live.leftGlade()));
        int penalty = AbyssConfig.MAZE_DEATH_PENALTY_SECONDS.get();
        if (penalty > 0) {
            player.displayClientMessage(Component.literal(
                    "§c✖ Caught. §7+" + penalty + "s on your run — the clock is still going."), false);
        }
    }

    /** Elapsed seconds including death penalties, or -1 if not running. */
    public static int elapsedSeconds(ServerLevel level, UUID id) {
        Live live = LIVE.get(id);
        if (live == null) {
            return -1;
        }
        int raw = (int) ((level.getGameTime() - live.startedTick()) / 20L);
        return raw + live.deaths() * AbyssConfig.MAZE_DEATH_PENALTY_SECONDS.get();
    }

    public static int deaths(UUID id) {
        Live live = LIVE.get(id);
        return live == null ? 0 : live.deaths();
    }

    /**
     * Ends a run at the exit. Records it, tells everyone, and returns the time so
     * the caller can decide what else to do about it.
     */
    public static int complete(ServerLevel level, ServerPlayer player, MazeData.Layout layout) {
        int seconds = elapsedSeconds(level, player.getUUID());
        if (seconds < 0) {
            return -1;
        }
        int deaths = deaths(player.getUUID());
        LIVE.remove(player.getUUID());

        MazeRuns store = get(level.getServer());
        boolean isBest = store.records.isEmpty() || seconds < store.records.get(0).seconds();
        store.add(new Record(player.getGameProfile().getName(), seconds, deaths,
                (int) MazeRuntime.dayNumber(level) + 1, layout == null ? "?" : layout.name()));

        String time = format(seconds);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(
                    "§6§l✦ " + player.getGameProfile().getName() + " ESCAPED THE MAZE §r§7— §e" + time
                            + (deaths > 0 ? " §7(" + deaths + " death" + (deaths > 1 ? "s" : "") + ")" : "")
                            + (isBest ? " §6§lNEW RECORD" : "")), false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        return seconds;
    }

    public static String format(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    // ------------------------------------------------------------------
    // Records
    // ------------------------------------------------------------------

    private void add(Record r) {
        records.add(r);
        records.sort(Comparator.comparingInt(Record::seconds));
        while (records.size() > TOP_N) {
            records.remove(records.size() - 1);
        }
        setDirty();
    }

    public List<Record> top() {
        return List.copyOf(records);
    }

    public static MazeRuns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static SavedData.Factory<MazeRuns> factory() {
        return new SavedData.Factory<>(MazeRuns::new, MazeRuns::load, null);
    }

    private static MazeRuns load(CompoundTag tag, HolderLookup.Provider provider) {
        MazeRuns out = new MazeRuns();
        ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            out.records.add(new Record(e.getString("name"), e.getInt("seconds"),
                    e.getInt("deaths"), e.getInt("day"), e.getString("layout")));
        }
        out.records.sort(Comparator.comparingInt(Record::seconds));
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Record r : records) {
            CompoundTag e = new CompoundTag();
            e.putString("name", r.name());
            e.putInt("seconds", r.seconds());
            e.putInt("deaths", r.deaths());
            e.putInt("day", r.day());
            e.putString("layout", r.layout());
            list.add(e);
        }
        tag.put("records", list);
        return tag;
    }
}
