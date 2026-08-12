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
 * The doors: one maze, one calendar.
 *
 * <p>The maze used to rotate through seven authored presets, which meant seven
 * whole layouts to hold in your head and a map that was only true one night in
 * seven. That is a lot of authored content buying surprisingly little play: what
 * a group actually learns is <em>one</em> maze, and what should keep it alive is
 * not swapping the whole thing but the walls moving underneath what you know.
 *
 * <p>So there is one layout, and every midnight a handful of its two hundred
 * doors change - some that were open close, some that were closed open. The
 * bones never move, and yesterday's map is almost entirely true: worth keeping,
 * worth trusting nearly everywhere, wrong in exactly the places that kill you.
 *
 * <h2>The calendar is fixed</h2>
 *
 * <p>The schedule depends on the day number and nothing else - not the session,
 * not the world seed. Day three of this game is day three of every game: the
 * same doors standing, the same portal live, the same breaches into the Dead
 * Glade. Veterans genuinely get to learn "on day three you go north", the way
 * you learn a place with weekdays - and because the whole calendar is one fixed
 * sequence, every day of it has been verified offline before it ever ships
 * (tools/verify_maze_presets.py walks the identical arithmetic).
 *
 * <h2>The guarantee</h2>
 *
 * <p>Every day's state is checked before it is adopted - day zero included:
 * all four Glade doors must reach the day's portal through the graph (with the
 * Dead Glade's footprint counted as solid, because its wall ring is), and the
 * camp must keep at least one breach opening onto live corridor. A flip set
 * that seals either is re-rolled; if every roll fails, the doors simply do not
 * move tonight; and if even that fails, the state reshapes from the atlas - an
 * authored layout verified to reach all seven portals. The verified calendar
 * never needs the atlas, so nights stay small; the rung exists so the promise
 * survives even a dataset edit. Underneath all of it the physical carve still
 * cuts seventy per cent of a route and warns an operator if it ever has to cut
 * the rest - "always a route" is the one promise this mode cannot break even
 * once.
 *
 * <h2>The portal moves too</h2>
 *
 * <p>Which of the seven portals is live is part of the same fixed calendar. It
 * can stay put two days running - a door that sometimes does not move is more
 * unsettling than one that always does, because you can never conclude
 * anything from it.
 *
 * <p>Saved, so a restart mid-game wakes up with the same doors standing that
 * were standing when it went down - and a fresh save walks the calendar forward
 * from day zero deterministically, so it lands on the identical state.
 */
public final class MazeDoors extends SavedData {

    public static final String NAME = "aztecabyss_maze_doors";

    /** How many doors move each midnight: a handful, out of two hundred. */
    private static final int FLIP_COUNT = 8;
    /** How many re-rolls a route-sealing flip set gets before we stop flipping. */
    private static final int RETRIES = 24;
    /**
     * The calendar's seed - standing where the session number used to, so the
     * schedule is the same in every game. Not arbitrary: chosen by offline
     * search (tools/verify_maze_presets.py) as a seed whose whole thirty-day
     * horizon passes on small flips alone - no held nights, no atlas nights -
     * whose day zero is the authored base itself, and whose first nine days
     * put every one of the seven portals on duty at least once. Change it and
     * the verifier must pass again first.
     */
    private static final int CAL = 0xCD4;

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

    /** Which portal is live on a given day - the same day every game. */
    private static String exitFor(int day) {
        String[] ids = PortalAnnex.EXIT_IDS;
        int h = Deco.hash(CAL, day, 0x0E17, 0x5EED) & 0x7FFFFFFF;
        return ids[h % ids.length];
    }

    /** Tonight's flip set: a handful of doors, drawn deterministically. */
    private static Set<String> flipped(Set<String> from, int day, int salt) {
        List<String> ids = sortedToggles();
        int want = FLIP_COUNT;
        Set<String> next = new LinkedHashSet<>(from);
        Set<String> chosen = new LinkedHashSet<>();
        for (int i = 0; chosen.size() < want && i < want * 6; i++) {
            String id = ids.get((Deco.hash(CAL, day, salt, i) & 0x7FFFFFFF) % ids.size());
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
    private static Set<String> fromAtlas(int day, String exit, Set<String> lastResort) {
        Set<String> atlas = atlasState();
        for (int salt = 0; salt < RETRIES; salt++) {
            Set<String> candidate = flipped(atlas, day, RETRIES + salt);
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
    // ------------------------------------------------------------------
    // The calendar itself: a pure function of the day, computed once
    // ------------------------------------------------------------------

    /** Every day's door state so far, index = day. Grows on demand, never shrinks. */
    private static final List<Set<String>> CALENDAR = new ArrayList<>();

    /**
     * The doors standing on a given day - the same answer in every game, every
     * save, every restart, because it is arithmetic on the day number and
     * nothing else. Walked forward one day at a time and cached, so the lore
     * book can quote day eight and the reshape can diff two days for the price
     * of a lookup.
     */
    public static synchronized Set<String> dayState(int day) {
        while (CALENDAR.size() <= day) {
            int d = CALENDAR.size();
            String exit = exitFor(d);
            Set<String> adopted;
            if (d == 0) {
                // Day zero is validated like any other: if the base cannot
                // reach the drawn portal, flip toward one that can, or take
                // the atlas outright.
                Set<String> base = baseState();
                adopted = acceptable(base, exit) ? base : null;
                for (int salt = 0; salt < RETRIES && adopted == null; salt++) {
                    Set<String> candidate = flipped(base, 0, salt);
                    if (acceptable(candidate, exit)) {
                        adopted = candidate;
                    }
                }
                if (adopted == null) {
                    adopted = fromAtlas(0, exit, base);
                }
            } else {
                Set<String> from = CALENDAR.get(d - 1);
                adopted = null;
                for (int salt = 0; salt < RETRIES && adopted == null; salt++) {
                    Set<String> candidate = flipped(from, d, salt);
                    if (acceptable(candidate, exit)) {
                        adopted = candidate;
                    }
                }
                if (adopted == null && acceptable(from, exit)) {
                    adopted = from; // the doors hold still tonight
                }
                if (adopted == null) {
                    adopted = fromAtlas(d, exit, flipped(from, d, 0));
                }
            }
            CALENDAR.add(java.util.Collections.unmodifiableSet(adopted));
        }
        return CALENDAR.get(day);
    }

    /** The toggles that move between one day and the next: the night's flips. */
    public static Set<String> changedOvernight(int newDay) {
        Set<String> out = new LinkedHashSet<>();
        if (newDay <= 0) {
            return out;
        }
        Set<String> before = dayState(newDay - 1);
        Set<String> after = dayState(newDay);
        for (String id : before) {
            if (!after.contains(id)) {
                out.add(id);
            }
        }
        for (String id : after) {
            if (!before.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Which portal a given day opens. Public for the lore book. */
    public static String exitOn(int day) {
        return exitFor(day);
    }

    public synchronized MazeData.Layout ensure(int forSession, int forDay) {
        if (cached == null || day != forDay || session != forSession) {
            session = forSession;
            day = forDay;
            exitId = exitFor(forDay);
            open.clear();
            open.addAll(dayState(forDay));
            // Named by day alone: the calendar is the same in every game, so
            // everything keyed off the name (breach picks, reshape detection)
            // repeats with it.
            cached = new MazeData.Layout("doors_day_" + forDay, exitId, 0,
                    List.copyOf(open));
            setDirty();
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
