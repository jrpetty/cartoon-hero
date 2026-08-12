package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.worldgen.Deco;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The doors: one maze, breathing.
 *
 * <p>The maze used to rotate through seven authored presets, which meant seven
 * whole layouts to hold in your head and a map that was only true one night in
 * seven. That is a lot of authored content buying surprisingly little play: what
 * a group actually learns is <em>one</em> maze, and what should keep it alive is
 * not swapping the whole thing but the walls moving underneath what you know.
 *
 * <p>So now there is one layout, and every midnight roughly ten per cent of its
 * two hundred doors change - some that were open close, some that were closed
 * open. The bones never move. Yesterday's map is ninety per cent true, which is
 * exactly the right amount: worth keeping, never worth trusting.
 *
 * <h2>The guarantee</h2>
 *
 * <p>Every day's state is checked before it is adopted - day zero included:
 * all four Glade doors must reach the day's portal through the graph, and the
 * Dead Glade must keep at least one breach opening onto live corridor. A flip
 * set that seals either is re-rolled up to eight times; if every roll fails,
 * the doors simply do not move tonight (yesterday's state is re-checked
 * against the new portal); and if even that fails, the state falls back to the
 * atlas - an authored layout verified to reach all seven portals with the camp
 * open and treated as solid. (The base layout cannot serve here: it reaches
 * only three of the seven.) Underneath all of it the physical carve still cuts
 * seventy per cent
 * of a route and warns an operator if it ever has to cut the rest - four
 * layers, because "always a route" is the one promise this mode cannot break
 * even once.
 *
 * <h2>The portal moves too</h2>
 *
 * <p>Which of the seven portals is live is drawn fresh each day. It can land on
 * the same one twice - a door that sometimes stays put is more unsettling than
 * one that always moves, because you can never conclude anything from it.
 *
 * <p>Saved, so a restart mid-game wakes up with the same doors standing that
 * were standing when it went down - and a fresh save walks the history forward
 * from day zero deterministically, so it lands on the identical state.
 */
public final class MazeDoors extends SavedData {

    public static final String NAME = "aztecabyss_maze_doors";

    /** Fraction of the doors that move each midnight: one in ten. */
    private static final int FLIP_DIVISOR = 10;
    /** How many re-rolls a route-sealing flip set gets before we stop flipping. */
    private static final int RETRIES = 8;

    private final Set<String> open = new LinkedHashSet<>();
    private int session = -1;
    private int day = -1;
    private String exitId = "";
    private MazeData.Layout cached;

    /** Toggle ids in a fixed order, so hashes index the same door everywhere. */
    private static List<String> sortedToggles() {
        List<String> ids = new ArrayList<>(MazeData.togglePoints().keySet());
        java.util.Collections.sort(ids);
        return ids;
    }

    /**
     * Toggles the reshape never writes: the ones standing on the Dead Glade.
     * Kept out of the graph as well, so the check never routes through a door
     * that is physically frozen shut.
     */
    private static boolean frozen(String id) {
        MazeData.TogglePoint tp = MazeData.togglePoints().get(id);
        if (tp == null) {
            return true;
        }
        for (String end : tp.edge().split(">")) {
            String[] parts = end.split(",");
            if (parts.length == 2) {
                try {
                    if (DeadGlade.coversCell(Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()))) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The authored opening state: the first layout in the dataset. */
    private static Set<String> baseState() {
        List<MazeData.Layout> layouts = MazeData.layouts();
        return layouts.isEmpty() ? new LinkedHashSet<>() : layoutState(layouts.get(0));
    }

    /**
     * Which authored layout the ladder falls back to when nothing gentler
     * works. Verified offline (tools/verify_maze_presets.py) to reach every
     * one of the seven portals with the Dead Glade treated as solid and its
     * camp still enterable - only day_4 and day_6 qualify; the base layout
     * misses portals outright, and day_7 loses three of them once routes may
     * not thread through the camp.
     */
    private static final String ATLAS_NAME = "day_6";

    private static Set<String> atlasState() {
        List<MazeData.Layout> layouts = MazeData.layouts();
        for (MazeData.Layout l : layouts) {
            if (ATLAS_NAME.equals(l.name())) {
                return layoutState(l);
            }
        }
        return layouts.isEmpty() ? new LinkedHashSet<>()
                : layoutState(layouts.get(layouts.size() - 1));
    }

    private static Set<String> layoutState(MazeData.Layout layout) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : layout.open()) {
            if (!frozen(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Which portal is live on a given day. Repeats are allowed on purpose. */
    private static String exitFor(int session, int day) {
        String[] ids = PortalAnnex.EXIT_IDS;
        int h = Deco.hash(session, day, 0x0E17, 0x5EED) & 0x7FFFFFFF;
        return ids[h % ids.length];
    }

    /** Tonight's flip set: about a tenth of the doors, drawn deterministically. */
    private static Set<String> flipped(Set<String> from, int session, int day, int salt) {
        List<String> ids = sortedToggles();
        int want = Math.max(1, ids.size() / FLIP_DIVISOR);
        Set<String> next = new LinkedHashSet<>(from);
        Set<String> chosen = new LinkedHashSet<>();
        for (int i = 0; chosen.size() < want && i < want * 6; i++) {
            String id = ids.get((Deco.hash(session, day, salt, i) & 0x7FFFFFFF) % ids.size());
            if (frozen(id) || !chosen.add(id)) {
                continue;
            }
            if (!next.remove(id)) {
                next.add(id);
            }
        }
        return next;
    }

    /**
     * The acceptance check every candidate state must pass: all four Glade
     * doors reach the portal, and the Dead Glade keeps at least one breach
     * opening onto live corridor - a reshape that sealed the camp used to slip
     * through and lock a day's content with nothing to say why.
     */
    private static boolean acceptable(Set<String> state, String exit) {
        MazeData.Layout probe = new MazeData.Layout("probe", exit, 0, List.copyOf(state));
        for (int[] door : MazeBuilder.DOOR_CELLS) {
            if (!MazeData.exitReachable(probe, door[0], door[1])) {
                return false;
            }
        }
        return !DeadGlade.breachesFor(probe).isEmpty();
    }

    /**
     * The last graph rung: reshape from the atlas instead of from yesterday.
     * Salted flips of the atlas are tried first so fallback nights still look
     * different from one another; only if all of those fail does the state
     * land on the atlas itself. If even THAT fails (it never has in
     * simulation), the given last resort is adopted and the physical carve
     * underneath cuts the route and tells an operator.
     */
    private static Set<String> fromAtlas(int session, int day, String exit, Set<String> lastResort) {
        Set<String> atlas = atlasState();
        for (int salt = 0; salt < RETRIES; salt++) {
            Set<String> candidate = flipped(atlas, session, day, RETRIES + salt);
            if (acceptable(candidate, exit)) {
                return candidate;
            }
        }
        return acceptable(atlas, exit) ? atlas : lastResort;
    }

    /**
     * Walks the doors forward to the given day and hands back its layout.
     *
     * <p>Advanced one day at a time even after a restart, so a save that comes
     * back mid-game recomputes the identical history a running server would
     * have lived through.
     */
    public synchronized MazeData.Layout ensure(int forSession, int forDay) {
        if (forSession != session || forDay < day || open.isEmpty()) {
            session = forSession;
            day = 0;
            exitId = exitFor(session, 0);
            open.clear();
            // Day zero is validated like any other: if the base cannot reach
            // the drawn portal (it misses one of the seven), flip toward one
            // that can, or take the atlas outright.
            Set<String> base = baseState();
            Set<String> opening = null;
            if (acceptable(base, exitId)) {
                opening = base;
            }
            for (int salt = 0; salt < RETRIES && opening == null; salt++) {
                Set<String> candidate = flipped(base, session, 0, salt);
                if (acceptable(candidate, exitId)) {
                    opening = candidate;
                }
            }
            if (opening == null) {
                opening = fromAtlas(session, 0, exitId, base);
            }
            open.addAll(opening);
            cached = null;
            setDirty();
        }
        while (day < forDay) {
            day++;
            exitId = exitFor(session, day);
            Set<String> adopted = null;
            for (int salt = 0; salt < RETRIES && adopted == null; salt++) {
                Set<String> candidate = flipped(open, session, day, salt);
                if (acceptable(candidate, exitId)) {
                    adopted = candidate;
                }
            }
            if (adopted == null && acceptable(open, exitId)) {
                adopted = new LinkedHashSet<>(open); // the doors hold still tonight
            }
            if (adopted == null) {
                adopted = fromAtlas(session, day, exitId, flipped(open, session, day, 0));
            }
            open.clear();
            open.addAll(adopted);
            cached = null;
            setDirty();
        }
        if (cached == null) {
            cached = new MazeData.Layout("night_" + session + "_" + day, exitId, 0,
                    List.copyOf(open));
        }
        return cached;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Open", String.join(",", open));
        tag.putInt("Session", session);
        tag.putInt("Day", day);
        tag.putString("Exit", exitId);
        return tag;
    }

    public static MazeDoors load(CompoundTag tag, HolderLookup.Provider registries) {
        MazeDoors out = new MazeDoors();
        String joined = tag.getString("Open");
        if (!joined.isEmpty()) {
            for (String id : joined.split(",")) {
                out.open.add(id);
            }
        }
        out.session = tag.getInt("Session");
        out.day = tag.getInt("Day");
        out.exitId = tag.getString("Exit");
        return out;
    }

    public static SavedData.Factory<MazeDoors> factory() {
        return new SavedData.Factory<>(MazeDoors::new, MazeDoors::load, null);
    }

    public static MazeDoors get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static MazeDoors get(ServerLevel level) {
        return get(level.getServer());
    }
}
