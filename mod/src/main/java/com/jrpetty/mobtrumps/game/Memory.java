package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Concentration, played with mob cards.
 *
 * <p>The rules live here, with no Minecraft on the classpath, so they compile
 * and run standalone and the regression harness can play whole games against
 * them. {@code MemoryManager} owns the players, the packets and the clock; this
 * owns only what is true about a board.
 *
 * <p>The face list is the secret of the game, which is why {@link #faceAt} will
 * not hand out a face the player has not turned over. A board that answered
 * every question truthfully would be perfectly correct and completely
 * pointless: the client could ask for all thirty-six and win in one move.
 */
public final class Memory {

    /** Tile states. Matched tiles stay on the table so you can see what has gone. */
    public static final int HIDDEN = 0;
    public static final int FACE_UP = 1;
    public static final int MATCHED = 2;

    /** What a flip did, which is all the caller needs to decide what happens next. */
    public enum Flip {
        /** Out of turn, mid-peek, out of range, or a card already turned over. */
        REJECTED,
        /** First of a pair — nothing to compare it with yet. */
        FIRST,
        /** Second of a pair, and they match. The player goes again. */
        MATCH,
        /** Second of a pair, and they do not. Both turn back after the peek. */
        MISS
    }

    public enum BoardSize {
        EASY("Easy", 4, 4),
        MEDIUM("Medium", 6, 4),
        HARD("Hard", 6, 6);

        public final String label;
        public final int cols;
        public final int rows;

        BoardSize(String label, int cols, int rows) {
            this.label = label;
            this.cols = cols;
            this.rows = rows;
        }

        public int tiles() {
            return cols * rows;
        }

        public int pairs() {
            return tiles() / 2;
        }

        public static BoardSize byOrdinal(int i) {
            BoardSize[] all = values();
            return all[Math.floorMod(i, all.length)];
        }
    }

    private Memory() {
    }

    /**
     * Lay out a board: {@code pairs} distinct mobs, twice each, shuffled.
     *
     * <p>Takes whatever pool it is given and does not care where it came from —
     * choosing between "mobs this player owns" and the full set is the
     * manager's business, because only the manager knows whose board it is.
     *
     * @throws IllegalArgumentException if the pool cannot fill the board, which
     *         is a caller bug: the fallback exists precisely so it cannot happen.
     */
    public static List<String> deal(List<String> pool, int pairs, Random rng) {
        if (pool.size() < pairs) {
            throw new IllegalArgumentException(
                    "pool of " + pool.size() + " cannot fill " + pairs + " pairs");
        }
        List<String> picks = new ArrayList<>(pool);
        Collections.shuffle(picks, rng);
        List<String> faces = new ArrayList<>(pairs * 2);
        for (int i = 0; i < pairs; i++) {
            faces.add(picks.get(i));
            faces.add(picks.get(i));
        }
        Collections.shuffle(faces, rng);
        return faces;
    }

    /** One board, mid-game. */
    public static final class Board {

        private final String[] faces;
        private final int[] state;
        private int firstUp = -1;
        private int secondUp = -1;
        private int moves;
        private int matched;

        public Board(List<String> faces) {
            this.faces = faces.toArray(new String[0]);
            this.state = new int[this.faces.length];
        }

        public int size() {
            return faces.length;
        }

        public int stateAt(int index) {
            return inRange(index) ? state[index] : HIDDEN;
        }

        /**
         * The mob on a tile, or {@code ""} while it is face down.
         *
         * <p>This is the whole security model of the game. Everything that
         * builds a packet reads faces through here, so a hidden face has no
         * path onto the wire even if a future caller forgets it should not.
         */
        public String faceAt(int index) {
            return inRange(index) && state[index] != HIDDEN ? faces[index] : "";
        }

        public int moves() {
            return moves;
        }

        public int matchedPairs() {
            return matched;
        }

        /** Two cards are up and unresolved: the board is showing a miss. */
        public boolean peeking() {
            return secondUp >= 0;
        }

        public boolean complete() {
            return matched * 2 == faces.length;
        }

        public Flip flip(int index) {
            if (!inRange(index) || peeking() || complete() || state[index] != HIDDEN) {
                return Flip.REJECTED;
            }
            state[index] = FACE_UP;
            if (firstUp < 0) {
                firstUp = index;
                return Flip.FIRST;
            }
            // A move is a PAIR of flips, so it is counted on the second one.
            // Counting every flip would double every score and make the solo
            // par times meaningless.
            moves++;
            if (faces[index].equals(faces[firstUp])) {
                state[index] = MATCHED;
                state[firstUp] = MATCHED;
                firstUp = -1;
                matched++;
                return Flip.MATCH;
            }
            secondUp = index;
            return Flip.MISS;
        }

        /** Turn the peeked pair back over. Safe to call when nothing is peeking. */
        public void resolvePeek() {
            if (!peeking()) {
                return;
            }
            state[firstUp] = HIDDEN;
            state[secondUp] = HIDDEN;
            firstUp = -1;
            secondUp = -1;
        }

        private boolean inRange(int index) {
            return index >= 0 && index < faces.length;
        }

        /** Every face, for the end-of-game reveal only. */
        public List<String> revealAll() {
            return List.of(faces);
        }
    }
}
