package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MemorySyncPayload;

/**
 * The last board the server sent, plus the client-side flip animation.
 *
 * <p>The client is told nothing about a face-down tile, so there is nothing
 * here to leak: this holds exactly the packet, and the screen draws exactly
 * this. The animation is the only thing invented locally — it is a way of
 * showing a change the server already made, never a prediction of one.
 */
public final class ClientMemory {

    /** How long a card takes to turn over, both halves together. */
    public static final long FLIP_MS = 220L;

    private static volatile MemorySyncPayload state = MemorySyncPayload.menu(0);
    /** When each tile last changed state, for the turn animation. */
    private static long[] changedAt = new long[0];
    private static int[] lastState = new int[0];
    private static int[] prevState = new int[0];

    private ClientMemory() {
    }

    public static void set(MemorySyncPayload payload) {
        int tiles = payload.tileCount();
        if (lastState.length != tiles) {
            lastState = new int[tiles];
            prevState = new int[tiles];
            changedAt = new long[tiles];
            // A brand new board should not animate every tile at once, so the
            // first sync is taken as the starting position rather than a change.
            for (int i = 0; i < tiles; i++) {
                lastState[i] = payload.stateAt(i);
                prevState[i] = lastState[i];
            }
        } else {
            long now = System.currentTimeMillis();
            for (int i = 0; i < tiles; i++) {
                int current = payload.stateAt(i);
                if (current != lastState[i]) {
                    prevState[i] = lastState[i];
                    lastState[i] = current;
                    changedAt[i] = now;
                }
            }
        }
        state = payload;
    }

    public static MemorySyncPayload state() {
        return state;
    }

    /**
     * How far through its turn a tile is, 0..1, or 1 when it is settled.
     * The card is drawn squeezed to |1 - 2t| and shows its old side until
     * halfway, which is what makes it read as turning rather than fading.
     */
    /**
     * Whether a tile's last change turned it over, as opposed to only
     * restyling it. A matched pair goes FACE_UP -> MATCHED without changing
     * which side is showing, and animating that would flash the card back for
     * a frame on every match.
     */
    public static boolean turning(int tile) {
        if (tile < 0 || tile >= lastState.length) {
            return false;
        }
        boolean was = prevState[tile] == com.jrpetty.mobtrumps.game.Memory.HIDDEN;
        boolean is = lastState[tile] == com.jrpetty.mobtrumps.game.Memory.HIDDEN;
        return was != is;
    }

    public static float flipProgress(int tile) {
        if (tile < 0 || tile >= changedAt.length || changedAt[tile] == 0) {
            return 1f;
        }
        long since = System.currentTimeMillis() - changedAt[tile];
        return since >= FLIP_MS ? 1f : since / (float) FLIP_MS;
    }
}
