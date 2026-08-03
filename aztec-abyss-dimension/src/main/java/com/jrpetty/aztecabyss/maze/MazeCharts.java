package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the Gladers know.
 *
 * <p>The best idea in the source material is that Runners go out, chart what they
 * find, and the map is assembled from what they bring back. The maze had the
 * ingredients for it - section colours banded into the corridor walls, signs and
 * torches you are allowed to place - and nothing that ever <em>collected</em> any
 * of it. The knowledge lived in the player's head or scrawled on a wall, and the
 * game itself never acknowledged that anybody had learned anything.
 *
 * <p>That was a real omission rather than a missing feature, because the maze is
 * built to reward exactly this. The nightly reshape only moves two hundred
 * toggles; the base graph underneath is stamped once and never touched again. So
 * the shape of the maze is durable knowledge, and a Runner charting it is doing
 * something permanently useful - which nothing in the game was willing to say.
 *
 * <h2>Two records, because there are two kinds of knowing</h2>
 *
 * <p>A player's own chart is what <em>they</em> have walked. The Glade's chart is
 * everything anybody has ever brought back. On a server the second is the point -
 * you inherit the work of every Runner before you, which is what makes a Glade a
 * settlement rather than a lobby. Solo, the two converge and nothing is lost.
 */
public final class MazeCharts extends SavedData {

    public static final String NAME = "aztecabyss_maze_charts";

    /** Cells in the grid, flattened. */
    private static final int CELLS = MazeData.GRID * MazeData.GRID;

    /** Everything anybody has charted. */
    private final BitSet glade = new BitSet(CELLS);
    /** Per player, what they personally have walked. */
    private final Map<UUID, BitSet> mine = new HashMap<>();
    /**
     * Cells a Builder has left a mark in.
     *
     * <p>Charting records where somebody <em>went</em>; this records where
     * somebody decided it was worth saying something. They are different facts
     * and the second is the more useful one, because a corridor you walked and
     * a corridor you walked and then flagged are not the same corridor. It is
     * Glade-wide with no per-player copy on purpose - a mark is a message left
     * for other people, so there is no private version of it.
     */
    private final BitSet marks = new BitSet(CELLS);

    private static int index(int cellX, int cellZ) {
        return cellZ * MazeData.GRID + cellX;
    }

    /**
     * Records a cell as charted.
     *
     * @return true if this was new to the Glade - worth announcing
     */
    public boolean chart(UUID who, int cellX, int cellZ) {
        if (cellX < 0 || cellZ < 0 || cellX >= MazeData.GRID || cellZ >= MazeData.GRID) {
            return false;
        }
        int i = index(cellX, cellZ);
        mine.computeIfAbsent(who, k -> new BitSet(CELLS)).set(i);
        if (glade.get(i)) {
            setDirty();
            return false;
        }
        glade.set(i);
        setDirty();
        return true;
    }

    /** Flags a cell as marked. Returns true if it was not already. */
    public boolean mark(int cellX, int cellZ) {
        if (cellX < 0 || cellZ < 0 || cellX >= MazeData.GRID || cellZ >= MazeData.GRID) {
            return false;
        }
        int i = index(cellX, cellZ);
        if (marks.get(i)) {
            return false;
        }
        marks.set(i);
        setDirty();
        return true;
    }

    /** Drops the flag when the last mark in a cell comes down. */
    public void unmark(int cellX, int cellZ) {
        if (cellX < 0 || cellZ < 0 || cellX >= MazeData.GRID || cellZ >= MazeData.GRID) {
            return;
        }
        marks.clear(index(cellX, cellZ));
        setDirty();
    }

    public boolean marked(int cellX, int cellZ) {
        return cellX >= 0 && cellZ >= 0 && cellX < MazeData.GRID && cellZ < MazeData.GRID
                && marks.get(index(cellX, cellZ));
    }

    public int markCount() {
        return marks.cardinality();
    }

    public boolean charted(int cellX, int cellZ) {
        return cellX >= 0 && cellZ >= 0 && cellX < MazeData.GRID && cellZ < MazeData.GRID
                && glade.get(index(cellX, cellZ));
    }

    public boolean chartedBy(UUID who, int cellX, int cellZ) {
        BitSet b = mine.get(who);
        return b != null && cellX >= 0 && cellZ >= 0
                && cellX < MazeData.GRID && cellZ < MazeData.GRID && b.get(index(cellX, cellZ));
    }

    /** How much of the maze the Glade has, ignoring the Glade's own cells. */
    public int gladePercent() {
        return percentOf(glade);
    }

    public int myPercent(UUID who) {
        BitSet b = mine.get(who);
        return b == null ? 0 : percentOf(b);
    }

    private static int percentOf(BitSet b) {
        int total = 0;
        int done = 0;
        for (int cx = 0; cx < MazeData.GRID; cx++) {
            for (int cz = 0; cz < MazeData.GRID; cz++) {
                if (MazeData.inGlade(cx, cz)) {
                    continue;
                }
                total++;
                if (b.get(index(cx, cz))) {
                    done++;
                }
            }
        }
        return total == 0 ? 0 : (done * 100) / total;
    }

    /**
     * How much of each compass section is charted.
     *
     * <p>The corridor walls are banded by section already, so "I came in through
     * the green" is a sentence a Runner can say. This is the other half of that:
     * the Glade can now answer which sections it actually understands.
     */
    public int[] sectionPercent() {
        int mid = MazeData.GRID / 2;
        int[] done = new int[4];
        int[] total = new int[4];
        for (int cx = 0; cx < MazeData.GRID; cx++) {
            for (int cz = 0; cz < MazeData.GRID; cz++) {
                if (MazeData.inGlade(cx, cz)) {
                    continue;
                }
                int q = (cz < mid ? 0 : 2) + (cx < mid ? 0 : 1);
                total[q]++;
                if (glade.get(index(cx, cz))) {
                    done[q]++;
                }
            }
        }
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = total[i] == 0 ? 0 : (done[i] * 100) / total[i];
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByteArray("Glade", glade.toByteArray());
        tag.putByteArray("Marks", marks.toByteArray());
        CompoundTag people = new CompoundTag();
        mine.forEach((id, bits) -> people.putByteArray(id.toString(), bits.toByteArray()));
        tag.put("Mine", people);
        return tag;
    }

    public static MazeCharts load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeCharts c = new MazeCharts();
        c.glade.or(BitSet.valueOf(tag.getByteArray("Glade")));
        c.marks.or(BitSet.valueOf(tag.getByteArray("Marks")));
        CompoundTag people = tag.getCompound("Mine");
        for (String key : people.getAllKeys()) {
            try {
                c.mine.put(UUID.fromString(key), BitSet.valueOf(people.getByteArray(key)));
            } catch (IllegalArgumentException ignored) {
                // A key that is not a uuid is not worth losing the rest of the file over.
            }
        }
        return c;
    }

    public static SavedData.Factory<MazeCharts> factory() {
        return new SavedData.Factory<>(MazeCharts::new, MazeCharts::load, null);
    }

    public static MazeCharts get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /**
     * The map, drawn for chat.
     *
     * <p>A window rather than the whole grid, because ninety-six cells across does
     * not fit on anybody's screen and a map you have to scroll is not a map. The
     * window is centred on the Glade, which is the only fixed point everybody
     * shares and the thing every route is described relative to.
     */
    public java.util.List<String> render(ServerPlayer viewer, int windowCells) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int centre = (MazeData.GLADE_MIN_CELL + MazeData.GLADE_MAX_CELL) / 2;
        int half = Math.max(4, windowCells / 2);
        int youX = viewer.blockPosition().getX() / MazeData.CELL;
        int youZ = viewer.blockPosition().getZ() / MazeData.CELL;

        for (int cz = centre - half; cz <= centre + half; cz++) {
            StringBuilder row = new StringBuilder();
            for (int cx = centre - half; cx <= centre + half; cx++) {
                if (cx == youX && cz == youZ) {
                    row.append("§b§l@");
                } else if (MazeData.inGlade(cx, cz)) {
                    row.append("§a▒");
                } else if (cx < 0 || cz < 0 || cx >= MazeData.GRID || cz >= MazeData.GRID) {
                    row.append("§8·");
                } else if (marked(cx, cz)) {
                    // A Builder's mark outranks everything except where you are
                    // standing, because somebody chose to put it there.
                    row.append("§e✚");
                } else if (chartedBy(viewer.getUUID(), cx, cz)) {
                    row.append("§f█");
                } else if (charted(cx, cz)) {
                    // Somebody else walked this. Dimmer, because inherited
                    // knowledge should look different from your own.
                    row.append("§7▓");
                } else {
                    row.append("§8·");
                }
            }
            out.add(row.toString());
        }
        return out;
    }
}
