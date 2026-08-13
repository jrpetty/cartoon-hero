package com.jrpetty.mobtrumps.game;

import java.util.List;
import java.util.UUID;

/**
 * A game of Memory in progress: whose turn it is, what everyone has taken, and
 * when the peeked pair turns back over.
 *
 * <p>This is here rather than in the manager so that it can be played. The
 * rules of a two-player match — a match keeps your turn, a miss passes it, only
 * after the peek, and never out of turn — used to live inside a class that
 * needs a running Minecraft server to instantiate, which meant the only way to
 * find out whether they worked was to start a server and take turns with
 * somebody. Now the harness plays several thousand complete games every build.
 *
 * <p>Clocks are passed in rather than read, so a test can hold time still and
 * step it exactly over the peek boundary.
 */
public final class MemoryMatch {

    /** How long a missed pair stays up, so both players get to see it. */
    public static final long PEEK_MS = 1400L;

    /** What a flip did to the match, as opposed to what it did to the board. */
    public enum Outcome {
        /** Not your turn, mid-peek, already taken, out of range, or game over. */
        REJECTED,
        /** First of a pair. */
        FIRST,
        /** A pair taken. The player goes again. */
        MATCH,
        /** No pair. The board is peeking; the turn passes when it resolves. */
        MISS
    }

    private final UUID a;
    private final UUID b;              // null for a solo board
    private final Memory.BoardSize size;
    private final Memory.Board board;
    private UUID turn;
    private int scoreA;
    private int scoreB;
    private long peekUntilMs;
    private boolean done;
    private UUID forfeited;

    public MemoryMatch(UUID a, UUID b, Memory.BoardSize size, List<String> faces) {
        this.a = a;
        this.b = b;
        this.size = size;
        this.board = new Memory.Board(faces);
        this.turn = a;
    }

    public boolean solo() {
        return b == null;
    }

    public UUID other(UUID id) {
        return id.equals(a) ? b : a;
    }

    public UUID turn() {
        return turn;
    }

    public boolean isTurn(UUID id) {
        return solo() || id.equals(turn);
    }

    public Memory.Board board() {
        return board;
    }

    public Memory.BoardSize size() {
        return size;
    }

    public boolean done() {
        return done;
    }

    public UUID forfeited() {
        return forfeited;
    }

    public boolean peeking() {
        return peekUntilMs > 0;
    }

    /** Milliseconds of peek left, for the client's "remember them" line. */
    public int peekLeftMs(long nowMs) {
        return peekUntilMs == 0 ? 0 : (int) Math.max(0, peekUntilMs - nowMs);
    }

    public int scoreOf(UUID id) {
        if (solo()) {
            return board.matchedPairs();
        }
        return id.equals(a) ? scoreA : scoreB;
    }

    /**
     * Turn a card over.
     *
     * <p>Everything that could make this illegal is checked here, so the packet
     * handler cannot forget one of them: the game being over, the peek being up,
     * and — the one that actually matters in a match — the player whose turn it
     * is not. The board itself then rejects a tile that is out of range or
     * already turned over.
     */
    public Outcome flip(UUID who, int tile, long nowMs) {
        if (done || peeking() || !isTurn(who)) {
            return Outcome.REJECTED;
        }
        Memory.Flip flip = board.flip(tile);
        switch (flip) {
            case FIRST:
                return Outcome.FIRST;
            case MATCH:
                if (!solo()) {
                    if (who.equals(a)) {
                        scoreA++;
                    } else {
                        scoreB++;
                    }
                }
                if (board.complete()) {
                    done = true;
                }
                return Outcome.MATCH;
            case MISS:
                peekUntilMs = nowMs + PEEK_MS;
                return Outcome.MISS;
            default:
                return Outcome.REJECTED;
        }
    }

    /**
     * Advance the clock. Returns true if anything changed, so the caller knows
     * whether it owes both players a packet.
     *
     * <p>The turn passes HERE rather than at the moment of the miss, which is
     * the whole point of the peek: both players watch the pair for the same
     * 1.4 seconds before the board takes it away.
     */
    public boolean tick(long nowMs) {
        if (done || !peeking() || nowMs < peekUntilMs) {
            return false;
        }
        peekUntilMs = 0;
        board.resolvePeek();
        if (!solo()) {
            turn = other(turn);
        }
        return true;
    }

    /** Somebody walked out. The other player takes it. */
    public void forfeit(UUID who) {
        if (done) {
            return;
        }
        done = true;
        forfeited = who;
        peekUntilMs = 0;
    }

    /**
     * Who won, or null for a draw, a solo board or a game still running.
     * A forfeit hands it to whoever stayed.
     */
    public UUID winner() {
        if (!done || solo()) {
            return null;
        }
        if (forfeited != null) {
            return other(forfeited);
        }
        return scoreA > scoreB ? a : scoreB > scoreA ? b : null;
    }
}
