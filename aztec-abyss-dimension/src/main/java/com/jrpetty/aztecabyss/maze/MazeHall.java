package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The hall of the out: everyone who has ever escaped, forever.
 *
 * <p>Deaths in this mode are permanent and loud; the win was a teleport and a
 * chat line. This is the other half of that weight - a ledger that survives
 * the game, the session, and the server restart, so "I got out of the maze"
 * is a fact with a record behind it rather than a story you tell.
 *
 * <p>Newest first, capped, and append-only: nothing a later game does can
 * take a line off it.
 */
public final class MazeHall extends SavedData {

    public static final String NAME = "aztecabyss_maze_hall";

    /** Enough for a very long-lived server; old glories scroll off the end. */
    private static final int CAP = 200;

    /** Packed {@code name|days|pct|kills|seconds|game}, newest first. */
    private final List<String> lines = new ArrayList<>();

    public void add(String name, int days, int pct, int kills, int seconds, int game) {
        lines.add(0, name + "|" + days + "|" + pct + "|" + kills + "|" + seconds + "|" + game);
        while (lines.size() > CAP) {
            lines.remove(lines.size() - 1);
        }
        setDirty();
    }

    /** The most recent escapes, newest first. */
    public List<String> recent(int count) {
        return List.copyOf(lines.subList(0, Math.min(count, lines.size())));
    }

    public int total() {
        return lines.size();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag out = new ListTag();
        for (String line : lines) {
            out.add(StringTag.valueOf(line));
        }
        tag.put("Lines", out);
        return tag;
    }

    public static MazeHall load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeHall out = new MazeHall();
        ListTag in = tag.getList("Lines", 8);
        for (int i = 0; i < in.size(); i++) {
            out.lines.add(in.getString(i));
        }
        return out;
    }

    public static SavedData.Factory<MazeHall> factory() {
        return new SavedData.Factory<>(MazeHall::new, MazeHall::load, null);
    }

    public static MazeHall get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }
}
