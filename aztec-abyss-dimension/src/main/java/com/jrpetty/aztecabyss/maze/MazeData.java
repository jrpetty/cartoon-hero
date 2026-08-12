package com.jrpetty.aztecabyss.maze;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Maze Runner dataset: the authored topology of the maze, loaded once from
 * the bundled {@code maze_config_v2.json}.
 *
 * <p>The dataset is the source of truth for the maze's <em>graph</em> - which
 * cell edges are open, which 200 of them can be toggled, where the seven exits
 * are, and which toggles each of the seven daily layouts opens. It was authored
 * once and is consumed, never regenerated.
 *
 * <h2>The scale trap</h2>
 * The file's own {@code meta} block still reads {@code cellSize: 16},
 * {@code wallHeight: 40}, and every block coordinate inside it - the toggle
 * points' {@code from}/{@code to} boxes, the exits' {@code portal} positions -
 * is quoted at that original scale. The live game runs at a six-block cell.
 *
 * <p>So: <strong>cell-level data is authoritative and block-level data in the
 * file is stale.</strong> Everything positional here is recomputed from cell
 * coordinates at {@link #CELL} and the file's own block figures are ignored
 * outright. Reading them back would put the maze at nearly three times its
 * real size, and silently - the walls would still build, just in the wrong
 * place and off the end of the map.
 */
public final class MazeData {

    /** Blocks per cell. Drives the entire scale of the map. */
    public static final int CELL = 6;
    /** Cells per side. 96 x 6 = a 576-block square. */
    public static final int GRID = 96;
    /** Full width of the map, in blocks. */
    public static final int SPAN = GRID * CELL;

    /**
     * Corridors are four wide, centred in the cell, leaving two-block walls.
     *
     * <p>They were two wide, which a Griever physically fits through and cannot
     * fight in - it is a scaled-up spider, so a two-block corridor meant it
     * filled the passage wall to wall and there was no getting past it, only
     * backwards. Four wide gives it room to move and you room to dodge, which is
     * the difference between a chase and a dead end.
     *
     * <p>Everything downstream reads these two numbers - corridor carving, the
     * wall test, the toggle writer - so the whole map rescales from here.
     */
    public static final int CORRIDOR_MIN = 1;
    public static final int CORRIDOR_MAX = 4;

    public static final int FLOOR_Y = 60;
    public static final int WALL_BASE_Y = 61;
    /** Walls stop below the dimension's build limit so they cannot be climbed over. */
    public static final int WALL_TOP_Y = 78;

    /** The Glade: cells 40-55 inclusive, i.e. blocks 240-335. */
    public static final int GLADE_MIN_CELL = 40;
    public static final int GLADE_MAX_CELL = 55;

    /** Where runners arrive, dead centre of the Glade. */
    public static final int SPAWN_X = 287;
    public static final int SPAWN_Y = 62;
    public static final int SPAWN_Z = 287;

    /** A movable wall segment: the edge it sits on, and whether it is route or loop. */
    public record TogglePoint(String id, String edge, boolean route) {
    }

    /** One of the seven ways out. Cell coordinates only - the portal is derived. */
    public record Exit(String id, int cellX, int cellZ, String facing) {
    }

    /** A day's maze: which exit is live, and which toggles stand open. */
    public record Layout(String name, String exit, int perfectRouteBlocks, List<String> open) {
    }

    private static Set<String> baseOpenEdges = Set.of();
    private static Map<String, TogglePoint> togglePoints = Map.of();
    private static Map<String, Exit> exits = Map.of();
    private static List<Layout> layouts = List.of();
    private static boolean loaded = false;

    private MazeData() {
    }

    /**
     * Reads the dataset off the classpath. Deliberately not a datapack lookup:
     * the maze's shape is code, not content, and loading it here means it cannot
     * arrive half-built or be reloaded out from under a run in progress.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = MazeData.class.getResourceAsStream(
                "/data/aztecabyss/maze/maze_config_v2.json")) {
            if (in == null) {
                throw new IllegalStateException("maze_config_v2.json missing from the jar");
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            Set<String> edges = new HashSet<>();
            for (var e : root.getAsJsonArray("baseOpenEdges")) {
                edges.add(e.getAsString());
            }
            baseOpenEdges = Set.copyOf(edges);

            Map<String, TogglePoint> tps = new HashMap<>();
            JsonObject tpObj = root.getAsJsonObject("togglePoints");
            for (String id : tpObj.keySet()) {
                JsonObject t = tpObj.getAsJsonObject(id);
                tps.put(id, new TogglePoint(id, t.get("edge").getAsString(),
                        "route".equals(t.get("kind").getAsString())));
            }
            togglePoints = Map.copyOf(tps);

            Map<String, Exit> ex = new HashMap<>();
            JsonObject exObj = root.getAsJsonObject("exits");
            for (String id : exObj.keySet()) {
                JsonObject x = exObj.getAsJsonObject(id);
                JsonArray cell = x.getAsJsonArray("cell");
                ex.put(id, new Exit(id, cell.get(0).getAsInt(), cell.get(1).getAsInt(),
                        x.get("facing").getAsString()));
            }
            exits = Map.copyOf(ex);

            List<Layout> lay = new ArrayList<>();
            for (var e : root.getAsJsonArray("layouts")) {
                JsonObject l = e.getAsJsonObject();
                List<String> open = new ArrayList<>();
                for (var o : l.getAsJsonArray("open")) {
                    open.add(o.getAsString());
                }
                lay.add(new Layout(l.get("name").getAsString(), l.get("exit").getAsString(),
                        l.get("perfectRouteBlocks").getAsInt(), List.copyOf(open)));
            }
            layouts = List.copyOf(lay);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the maze dataset", e);
        }
    }

    // ------------------------------------------------------------------
    // Graph
    // ------------------------------------------------------------------

    /** The dataset's edge key for two adjacent cells, in its own ordering. */
    public static String edgeKey(int ax, int az, int bx, int bz) {
        return ax + "," + az + ">" + bx + "," + bz;
    }

    /**
     * Whether the wall between two adjacent cells stands open for a given day.
     *
     * <p>An edge is open if the base maze has it open, or if the day's layout
     * lists a toggle point sitting on it. That is the whole trick of the nightly
     * reshape: the base graph never changes, only which of the 200 toggles are
     * pulled, so every layout is guaranteed solvable to its own fixed exit.
     */
    public static boolean isOpen(int ax, int az, int bx, int bz, Layout layout) {
        String a = edgeKey(ax, az, bx, bz);
        String b = edgeKey(bx, bz, ax, az);
        if (baseOpenEdges.contains(a) || baseOpenEdges.contains(b)) {
            return true;
        }
        if (layout == null) {
            return false;
        }
        for (String id : layout.open()) {
            TogglePoint tp = togglePoints.get(id);
            if (tp != null && (tp.edge().equals(a) || tp.edge().equals(b))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Can you get from this cell to today's exit, walking only what is open?
     *
     * <p>Nothing ever asked this question. The maze promised a way out and then
     * carved one by brute force rather than checking whether it already had one,
     * which meant two things were true at once and neither was noticed: the
     * carve never actually reached an exit, and it was cutting a motorway
     * through the middle of the maze on the way to not reaching one.
     *
     * <p>So ask first. All seven datasets connect on their own, which makes the
     * carve a fallback that never fires - and a fallback that never fires is the
     * only kind worth having.
     *
     * <p>The Glade is excluded on purpose: a route that crosses the clearing is
     * not a route through the maze.
     */
    public static boolean exitReachable(Layout layout, int fromX, int fromZ) {
        if (layout == null) {
            return false;
        }
        Exit ex = exit(layout.exit());
        if (ex == null) {
            return false;
        }
        boolean[] seen = new boolean[GRID * GRID];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        seen[fromZ * GRID + fromX] = true;
        queue.add(new int[]{fromX, fromZ});
        int[][] steps = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (at[0] == ex.cellX() && at[1] == ex.cellZ()) {
                return true;
            }
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];
                if (nx < 0 || nz < 0 || nx >= GRID || nz >= GRID) {
                    continue;
                }
                // The Dead Glade counts as solid: its wall ring physically
                // seals those cells bar a few one-block breaches, so a route
                // that only exists through the camp is not a route. The check
                // must be at least as strict as the world it vouches for.
                if (seen[nz * GRID + nx] || inGlade(nx, nz) || DeadGlade.coversCell(nx, nz)) {
                    continue;
                }
                if (!isOpen(at[0], at[1], nx, nz, layout)) {
                    continue;
                }
                seen[nz * GRID + nx] = true;
                queue.add(new int[]{nx, nz});
            }
        }
        return false;
    }

    public static List<Layout> layouts() {
        return layouts;
    }

    public static Layout layout(int index) {
        return layouts.isEmpty() ? null : layouts.get(Math.floorMod(index, layouts.size()));
    }

    public static Exit exit(String id) {
        return exits.get(id);
    }

    public static Map<String, TogglePoint> togglePoints() {
        return togglePoints;
    }

    // ------------------------------------------------------------------
    // Geometry, all recomputed at CELL - never read from the file
    // ------------------------------------------------------------------

    /** Lowest block of a cell's corridor along one axis. */
    public static int corridorMin(int cell) {
        return cell * CELL + CORRIDOR_MIN;
    }

    public static int corridorMax(int cell) {
        return cell * CELL + CORRIDOR_MAX;
    }

    public static boolean inGlade(int cellX, int cellZ) {
        return cellX >= GLADE_MIN_CELL && cellX <= GLADE_MAX_CELL
                && cellZ >= GLADE_MIN_CELL && cellZ <= GLADE_MAX_CELL;
    }

    /** Block bounds of the Glade clearing: 240 to 335 on both axes. */
    public static int gladeMinBlock() {
        return GLADE_MIN_CELL * CELL;
    }

    public static int gladeMaxBlock() {
        return (GLADE_MAX_CELL + 1) * CELL - 1;
    }

    /** The block a given exit's portal stands on, derived from its cell and facing. */
    public static int[] exitPortal(Exit exit) {
        int x = corridorMin(exit.cellX());
        int z = corridorMin(exit.cellZ());
        return switch (exit.facing()) {
            case "north" -> new int[]{x, FLOOR_Y + 1, exit.cellZ() * CELL};
            case "south" -> new int[]{x, FLOOR_Y + 1, (exit.cellZ() + 1) * CELL - 1};
            case "west" -> new int[]{exit.cellX() * CELL, FLOOR_Y + 1, z};
            default -> new int[]{(exit.cellX() + 1) * CELL - 1, FLOOR_Y + 1, z};
        };
    }
}
