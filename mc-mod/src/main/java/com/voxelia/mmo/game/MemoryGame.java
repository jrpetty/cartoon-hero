package com.voxelia.mmo.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * One game of Memory (Concentration). The server owns every card; clients are only
 * ever told the faces of cards that are currently revealed, so the board can't be
 * read out of a packet dump.
 *
 * <p>Solo is a single-seat game scored by moves and time. Versus is the classic
 * turn-based rules: flip two, keep the turn on a match, hand it over on a miss
 * after a short peek so both players get to see the mismatch.
 */
public final class MemoryGame {

    /** Board sizes. Every board has an even number of cells. */
    public enum Difficulty {
        EASY("Easy", 4, 4),
        MEDIUM("Medium", 6, 4),
        HARD("Hard", 6, 6);

        private final String display;
        private final int cols;
        private final int rows;

        Difficulty(String display, int cols, int rows) {
            this.display = display;
            this.cols = cols;
            this.rows = rows;
        }

        public String display() { return display; }
        public int cols() { return cols; }
        public int rows() { return rows; }
        public int pairs() { return cols * rows / 2; }

        public static Difficulty byName(String s) {
            for (Difficulty d : values()) {
                if (d.name().equalsIgnoreCase(s)) return d;
            }
            return null;
        }
    }

    public static final int HIDDEN = 0;
    public static final int FACE_UP = 1;
    public static final int MATCHED = 2;

    /** Ticks a mismatched pair stays visible before flipping back (1.4s). */
    private static final int PEEK_TICKS = 28;

    private final Difficulty difficulty;
    private final int[] faces;       // face id per card (a pair shares an id)
    private final int[] state;       // HIDDEN / FACE_UP / MATCHED
    private final List<UUID> players = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Integer> scores = new ArrayList<>();

    private int firstPick = -1;
    private int secondPick = -1;
    private int peekTicks = 0;       // >0 while a mismatch is on show
    private int turn = 0;
    private int moves = 0;
    private int pairsLeft;
    private boolean finished = false;
    private boolean justStarted = true;
    private long startedAtMillis;
    private long finishedAtMillis;

    public MemoryGame(Difficulty difficulty, long seed) {
        this.difficulty = difficulty;
        int cells = difficulty.cols() * difficulty.rows();
        this.faces = new int[cells];
        this.state = new int[cells];
        this.pairsLeft = cells / 2;

        // Faces come from the mod's own cards, skills first (see MemoryDeck).
        Random rng = new Random(seed);
        List<Integer> pool = MemoryDeck.pool(rng);

        List<Integer> deck = new ArrayList<>(cells);
        for (int i = 0; i < cells / 2; i++) {
            int face = pool.get(i % pool.size());
            deck.add(face);
            deck.add(face);
        }
        Collections.shuffle(deck, rng);
        for (int i = 0; i < cells; i++) faces[i] = deck.get(i);
        this.startedAtMillis = System.currentTimeMillis();
    }

    public void addPlayer(UUID id, String name) {
        players.add(id);
        names.add(name);
        scores.add(0);
    }

    public Difficulty difficulty() { return difficulty; }
    public List<UUID> players() { return players; }
    public List<String> names() { return names; }
    public List<Integer> scores() { return scores; }
    public boolean versus() { return players.size() > 1; }
    public boolean finished() { return finished; }
    public int turn() { return turn; }
    public int moves() { return moves; }
    public int pairsLeft() { return pairsLeft; }
    public int cards() { return faces.length; }
    public int stateOf(int i) { return state[i]; }
    public int faceOf(int i) { return faces[i]; }
    public int peekMillis() { return peekTicks * 50; }

    /** Seconds elapsed — frozen once the board is cleared. */
    public int elapsedSeconds() {
        long end = finished ? finishedAtMillis : System.currentTimeMillis();
        return (int) Math.max(0, (end - startedAtMillis) / 1000L);
    }

    public int indexOf(UUID player) { return players.indexOf(player); }

    public boolean isTurnOf(UUID player) {
        int i = indexOf(player);
        return i >= 0 && i == turn;
    }

    /** True on the first sync after creation, so clients know to pop the screen open. */
    public boolean consumeJustStarted() {
        boolean was = justStarted;
        justStarted = false;
        return was;
    }

    /**
     * Counts down the mismatch peek. Returns true when the board changed and every
     * seat needs a fresh sync.
     */
    public boolean tick() {
        if (finished || peekTicks <= 0) return false;
        peekTicks--;
        if (peekTicks > 0) return false;
        hidePicks();
        passTurn();
        return true;
    }

    /**
     * Flips one card for {@code player}. Returns true when something changed (so the
     * caller re-syncs); false for every rejected click — wrong turn, mid-peek,
     * already-revealed card, out of range.
     */
    public boolean flip(UUID player, int index) {
        if (finished || peekTicks > 0) return false;
        if (index < 0 || index >= faces.length) return false;
        if (!isTurnOf(player)) return false;
        if (state[index] != HIDDEN) return false;

        state[index] = FACE_UP;
        if (firstPick < 0) {
            firstPick = index;
            return true;
        }

        secondPick = index;
        moves++;
        if (faces[firstPick] == faces[secondPick]) {
            state[firstPick] = MATCHED;
            state[secondPick] = MATCHED;
            scores.set(turn, scores.get(turn) + 1);
            firstPick = -1;
            secondPick = -1;
            pairsLeft--;
            if (pairsLeft <= 0) {
                finished = true;
                finishedAtMillis = System.currentTimeMillis();
            }
            return true; // a match keeps the turn
        }

        peekTicks = PEEK_TICKS; // show the miss, then tick() hides it and passes the turn
        return true;
    }

    /** The winning seat index, or -1 on a draw / unfinished game. */
    public int winner() {
        if (!finished || !versus()) return -1;
        int best = 0;
        boolean tie = false;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > scores.get(best)) {
                best = i;
                tie = false;
            } else if (scores.get(i).equals(scores.get(best))) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    /** Drops a seat mid-game (disconnect / forfeit); the game ends if nobody is left to play. */
    public void forfeit(UUID player) {
        int i = indexOf(player);
        if (i < 0) return;
        players.remove(i);
        names.remove(i);
        scores.remove(i);
        if (players.size() <= 1) {
            finished = true;
            finishedAtMillis = System.currentTimeMillis();
        }
        if (turn >= players.size()) turn = 0;
    }

    /** m:ss for the solo clock. */
    public static String formatTime(int seconds) {
        int m = seconds / 60, sec = seconds % 60;
        return m + ":" + (sec < 10 ? "0" + sec : String.valueOf(sec));
    }

    private void hidePicks() {
        if (firstPick >= 0 && state[firstPick] == FACE_UP) state[firstPick] = HIDDEN;
        if (secondPick >= 0 && state[secondPick] == FACE_UP) state[secondPick] = HIDDEN;
        firstPick = -1;
        secondPick = -1;
    }

    private void passTurn() {
        if (players.size() > 1) turn = (turn + 1) % players.size();
    }
}
