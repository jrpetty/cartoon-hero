package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.GuessWhoSyncPayload;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The client's copy of a Guess Who board.
 *
 * <p>{@link #alive} is not computed here — it arrives from the server already
 * narrowed. The client's only job is to draw the difference, which is why it
 * also remembers the previous set: the faces that have just gone are the ones
 * worth animating, and knowing which they were is what makes the board flip
 * them over rather than blink them out.
 */
public final class ClientGuessWho {

    /** One question that was asked and the answer that came back. */
    public record Asked(int template, int value, boolean answer) {
    }

    private static volatile int phase;
    private static volatile int asked;
    private static volatile Set<String> alive = Set.of();
    /** Faces struck off by the most recent answer, for the flip animation. */
    private static volatile Set<String> justEliminated = Set.of();
    private static volatile List<Asked> log = List.of();
    private static volatile String secret = "";
    private static volatile long changedAt;

    private ClientGuessWho() {
    }

    public static void set(GuessWhoSyncPayload payload) {
        Set<String> next = new LinkedHashSet<>(payload.alive());
        Set<String> gone = new LinkedHashSet<>(alive);
        gone.removeAll(next);
        // a brand new board is not "everything was eliminated"
        justEliminated = next.size() > alive.size() ? Set.of() : Set.copyOf(gone);
        alive = Set.copyOf(next);
        phase = payload.phase();
        asked = payload.asked();
        secret = payload.secret() == null ? "" : payload.secret();
        List<Asked> parsed = new java.util.ArrayList<>();
        for (String line : payload.log()) {
            String[] bits = line.split("\\|", 3);
            if (bits.length != 3) {
                continue;
            }
            try {
                parsed.add(new Asked(Integer.parseInt(bits[0]), Integer.parseInt(bits[1]),
                        "1".equals(bits[2])));
            } catch (NumberFormatException ignored) {
                // a malformed line is dropped rather than allowed to throw mid-render
            }
        }
        log = List.copyOf(parsed);
        changedAt = System.currentTimeMillis();
    }

    public static boolean isAlive(String mobId) {
        return alive.contains(mobId);
    }

    public static boolean justEliminated(String mobId) {
        return justEliminated.contains(mobId);
    }

    public static int aliveCount() {
        return alive.size();
    }

    public static int asked() {
        return asked;
    }

    public static int phase() {
        return phase;
    }

    public static List<Asked> log() {
        return log;
    }

    /** The hidden mob, once the game is over. Empty while it is still hidden. */
    public static String secret() {
        return secret;
    }

    public static long changedAt() {
        return changedAt;
    }

    public static boolean playing() {
        return phase == com.jrpetty.mobtrumps.GuessWhoManager.PHASE_PLAYING;
    }
}
