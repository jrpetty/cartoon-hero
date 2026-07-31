package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A synchronised race: everyone on the same clock, from the same standing start,
 * for the same exit.
 *
 * <p>Ordinary runs are private things - you leave the Glade when you like and
 * your time is yours. A race is the opposite, and needs to be, because the maze
 * only becomes an argument once two people have run the same layout on the same
 * day and one of them was faster.
 *
 * <p>Everyone is pulled back to the Glade, given a three-second countdown, and
 * released together. The first to reach the exit takes it; the race record
 * persists with the world separately from the ordinary leaderboard, because a
 * standing start and a rolling start are not the same achievement.
 */
public final class MazeRace extends SavedData {

    public static final String NAME = "aztecabyss_maze_race";

    private static boolean active = false;
    private static long startsAtTick = -1;
    private static final Set<UUID> FIELD = new HashSet<>();

    private String recordName = "";
    private int recordSeconds = 0;

    public MazeRace() {
    }

    public static boolean isActive() {
        return active;
    }

    /** True once the countdown has run out and the field is away. */
    public static boolean isRunning(ServerLevel level) {
        return active && startsAtTick >= 0 && level.getGameTime() >= startsAtTick;
    }

    public static boolean isRacing(UUID id) {
        return active && FIELD.contains(id);
    }

    /**
     * Calls the field to the line. Everyone currently in the maze is pulled back
     * to the Glade and held for a three-second countdown.
     */
    public static boolean start(ServerLevel level) {
        if (active) {
            return false;
        }
        if (level.players().isEmpty()) {
            return false;
        }
        active = true;
        FIELD.clear();
        startsAtTick = level.getGameTime() + 60L;
        for (ServerPlayer p : level.players()) {
            FIELD.add(p.getUUID());
            MazeRuns.abandon(p.getUUID());
            p.teleportTo(level, MazeData.SPAWN_X + 0.5, MazeData.SPAWN_Y, MazeData.SPAWN_Z + 0.5,
                    Set.of(), 0.0F, 0.0F);
            p.displayClientMessage(Component.literal("§6§lRACE — to the line"), false);
        }
        return true;
    }

    public static void stop(ServerLevel level, String why) {
        if (!active) {
            return;
        }
        active = false;
        startsAtTick = -1;
        FIELD.clear();
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal("§7Race " + why + "."), false);
        }
    }

    /** Runs the countdown, and starts everyone's clock on the same tick. */
    public static void tick(ServerLevel level) {
        if (!active || startsAtTick < 0) {
            return;
        }
        long left = startsAtTick - level.getGameTime();
        if (left > 0) {
            if (left % 20L == 0L) {
                int secs = (int) (left / 20L);
                for (ServerPlayer p : level.players()) {
                    p.displayClientMessage(Component.literal("§e§l" + secs + "..."), true);
                    level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND,
                            SoundSource.PLAYERS, 0.6F, 1.8F);
                }
            }
            return;
        }
        if (left == 0L) {
            for (ServerPlayer p : level.players()) {
                if (!FIELD.contains(p.getUUID())) {
                    continue;
                }
                MazeRuns.begin(level, p);
                p.displayClientMessage(Component.literal("§a§lGO"), true);
                level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.0F, 1.6F);
            }
        }
    }

    /**
     * Someone in the field reached the exit. The first one home ends it.
     *
     * @return true if this escape won the race
     */
    public static boolean onEscape(ServerLevel level, ServerPlayer player, int seconds) {
        if (!active || !FIELD.contains(player.getUUID())) {
            return false;
        }
        MazeRace store = get(level.getServer());
        boolean record = store.recordSeconds <= 0 || seconds < store.recordSeconds;
        if (record) {
            store.recordName = player.getGameProfile().getName();
            store.recordSeconds = seconds;
            store.setDirty();
        }
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(
                    "§6§l🏁 " + player.getGameProfile().getName() + " TAKES THE RACE §r§7— §e"
                            + MazeRuns.format(seconds)
                            + (record ? " §6§lRACE RECORD" : "")), false);
        }
        stop(level, "over");
        return true;
    }

    public String recordName() {
        return recordName;
    }

    public int recordSeconds() {
        return recordSeconds;
    }

    public static MazeRace get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static SavedData.Factory<MazeRace> factory() {
        return new SavedData.Factory<>(MazeRace::new, MazeRace::load, null);
    }

    private static MazeRace load(CompoundTag tag, HolderLookup.Provider provider) {
        MazeRace out = new MazeRace();
        out.recordName = tag.getString("recordName");
        out.recordSeconds = tag.getInt("recordSeconds");
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("recordName", recordName);
        tag.putInt("recordSeconds", recordSeconds);
        return tag;
    }
}
