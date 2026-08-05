package com.gadgets;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Where a Command Hub leaves its latest board so a Base Tablet can read it from
 * anywhere.
 *
 * <p>The tablet does not reach across the world and read the hub directly. A hub
 * a thousand blocks away is in an unloaded chunk, and nothing in this mod forces
 * a chunk to load — so instead the hub leaves a copy of its board here every
 * time the board changes, and stamps the time whenever it ticks at all.
 *
 * <p>That distinction is the honest part. A tablet always shows the last board
 * the hub published <em>and how long ago it was heard from</em>. When your base
 * is loaded that is live to the second; when it is not, the reading is however
 * old the last visit was — which is correct rather than merely convenient,
 * because an unloaded base has no news. Its farms are not running either.
 *
 * <p>Nothing here is saved to disk. A hub republishes within a second of being
 * loaded, and a tablet whose hub has not been near a loaded chunk since the
 * server started says so rather than showing a board from a previous session.
 */
public final class HubRegistry {

    /**
     * @param name  the hub's own label, for the tablet's header
     * @param dim   dimension id, so the tablet can say where it is reporting from
     * @param pos   packed position, same reason
     * @param board the hub's sync tag — everything the board screen needs
     * @param heard game time the hub last ticked
     */
    public record Report(String name, String dim, long pos, CompoundTag board, long heard) {
    }

    private static final Map<String, Report> HUBS = new HashMap<>();

    private HubRegistry() {
    }

    /** Is some hub already answering on this code? */
    public static synchronized boolean taken(String code) {
        return HUBS.containsKey(code);
    }

    /**
     * Record a hub's board.
     *
     * @return false when a <em>different</em> hub already answers on this code,
     *         which is the caller's cue to mint itself a new one. Codes are
     *         checked for collisions when they are minted, but this table does
     *         not survive a restart, so two hubs built in separate sessions
     *         could in principle load holding the same eight digits. Rather
     *         than let the second silently overwrite the first — one tablet
     *         quietly reading the wrong base — the loser re-rolls.
     */
    public static synchronized boolean publish(String code, String name, String dim, long pos,
                                               CompoundTag board, long now) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        Report was = HUBS.get(code);
        if (was != null && !isSame(was, dim, pos)) {
            return false;
        }
        HUBS.put(code, new Report(name, dim, pos, board, now));
        return true;
    }

    /**
     * Whether a stored report belongs to the hub asking about it.
     *
     * <p>Dimension as well as position: coordinates repeat across dimensions,
     * and a nether outpost built at the overworld coordinates it links to is not
     * an accident, it is how everybody builds. Comparing positions alone would
     * make those two hubs the same hub.
     */
    private static boolean isSame(Report report, String dim, long pos) {
        return report.pos() == pos && report.dim().equals(dim);
    }

    /**
     * Note that a hub is still alive without recopying its board — the common
     * case, since a base that is running normally has nothing new to say.
     *
     * @return false when this hub has nothing on record here yet, so the caller
     *         knows to publish in full instead
     */
    public static synchronized boolean heard(String code, String dim, long pos, long now) {
        Report was = HUBS.get(code);
        if (was == null || !isSame(was, dim, pos)) {
            return false;
        }
        HUBS.put(code, new Report(was.name(), was.dim(), was.pos(), was.board(), now));
        return true;
    }

    @Nullable
    public static synchronized Report get(String code) {
        return code == null || code.isEmpty() ? null : HUBS.get(code);
    }

    /**
     * Stop answering on this code, but only for the hub that owns it — a
     * rebuilt hub that happened to roll the same digits keeps its own entry.
     */
    public static synchronized void forget(String code, String dim, long pos) {
        Report was = code == null ? null : HUBS.get(code);
        if (was != null && isSame(was, dim, pos)) {
            HUBS.remove(code);
        }
    }

    /** Drop everything — called when a server stops, so nothing leaks into the next world. */
    public static synchronized void clear() {
        HUBS.clear();
    }
}
