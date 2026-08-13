package com.voxelia.mmo.client;

import com.voxelia.mmo.game.MemoryGame;
import com.voxelia.mmo.network.MemoryStatePayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** Client-side mirror of the Memory board, plus the per-card flip animation clocks. */
public final class ClientMemory {
    private ClientMemory() {}

    /** How long a card takes to turn over (ms). */
    public static final long FLIP_MS = 220;

    private static List<Integer> board = List.of();
    private static List<String> names = List.of();
    private static List<Integer> meta = List.of();
    private static long[] flipStart = new long[0];
    private static int[] lastState = new int[0];
    private static int[] lastFace = new int[0]; // so a closing card can finish its flip in colour
    private static long syncedAt;
    private static int prevPairsLeft = -1;
    private static boolean prevFinished;

    public static void update(MemoryStatePayload payload) {
        List<Integer> incoming = payload.board();
        boolean newBoard = incoming.size() != board.size();
        if (newBoard) { // new board: reset the animation clocks
            flipStart = new long[incoming.size()];
            lastState = new int[incoming.size()];
            lastFace = new int[incoming.size()];
            java.util.Arrays.fill(lastFace, -1);
            prevPairsLeft = -1;
            prevFinished = false;
        }
        long now = Util.getMillis();
        boolean revealedSomething = false;
        for (int i = 0; i < incoming.size(); i++) {
            int st = incoming.get(i) >>> 6;
            if (i < lastState.length && st != lastState[i]) {
                // Only a hide/reveal flips the card; matching in place shouldn't spin it.
                if (st == MemoryGame.HIDDEN || lastState[i] == MemoryGame.HIDDEN) flipStart[i] = now;
                if (st == MemoryGame.FACE_UP && lastState[i] == MemoryGame.HIDDEN) revealedSomething = true;
                lastState[i] = st;
            }
            int face = (incoming.get(i) & 63) - 1;
            if (face >= 0 && i < lastFace.length) lastFace[i] = face;
        }
        board = incoming;
        names = payload.names();
        meta = payload.meta();
        syncedAt = now;

        boolean opening = metaAt(MemoryStatePayload.M_OPENING) == 1;
        if (!board.isEmpty() && opening) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof MemoryScreen)) mc.setScreen(new MemoryScreen());
        }

        // Audio cues, derived from what changed rather than sent down the wire.
        if (!board.isEmpty() && !opening) {
            int pairsNow = metaAt(MemoryStatePayload.M_PAIRS_LEFT);
            boolean finishedNow = metaAt(MemoryStatePayload.M_FINISHED) == 1;
            if (finishedNow && !prevFinished) cue(1.9f);
            else if (prevPairsLeft >= 0 && pairsNow < prevPairsLeft) cue(1.5f);
            else if (metaAt(MemoryStatePayload.M_PEEK_MS) > 0) cue(0.7f);
            else if (revealedSomething) cue(1.2f);
            prevPairsLeft = pairsNow;
            prevFinished = finishedNow;
        } else if (!board.isEmpty()) {
            prevPairsLeft = metaAt(MemoryStatePayload.M_PAIRS_LEFT);
            prevFinished = metaAt(MemoryStatePayload.M_FINISHED) == 1;
        }
    }

    private static void cue(float pitch) {
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    public static void clear() {
        board = List.of();
        names = List.of();
        meta = List.of();
    }

    public static boolean inGame() { return !board.isEmpty(); }

    public static int cards() { return board.size(); }

    public static int stateOf(int i) { return board.get(i) >>> 6; }

    /** Face id of a revealed card, or -1 while it is face down. */
    public static int faceOf(int i) { return (board.get(i) & 63) - 1; }

    /** Face to draw mid-flip: the live one, falling back to the last one we saw. */
    public static int faceForRender(int i) {
        int face = faceOf(i);
        return face >= 0 ? face : (i < lastFace.length ? lastFace[i] : -1);
    }

    /** 0..1 progress of card {@code i}'s flip animation. */
    public static float flipT(int i) {
        if (i >= flipStart.length || flipStart[i] == 0L) return 1f;
        return Math.min(1f, (Util.getMillis() - flipStart[i]) / (float) FLIP_MS);
    }

    public static List<String> names() { return names; }

    public static int cols() { return metaAt(MemoryStatePayload.M_COLS); }
    public static int rows() { return metaAt(MemoryStatePayload.M_ROWS); }
    public static int turn() { return metaAt(MemoryStatePayload.M_TURN); }
    public static int you() { return metaAt(MemoryStatePayload.M_YOU); }
    public static int moves() { return metaAt(MemoryStatePayload.M_MOVES); }
    public static int pairsLeft() { return metaAt(MemoryStatePayload.M_PAIRS_LEFT); }
    public static boolean finished() { return metaAt(MemoryStatePayload.M_FINISHED) == 1; }
    /** Winning seat, or -1 for a draw / solo / unfinished. */
    public static int winner() { return metaAt(MemoryStatePayload.M_WINNER) - 1; }
    public static boolean versus() { return names.size() > 1; }
    public static boolean yourTurn() { return you() >= 0 && you() == turn() && !finished(); }

    /** True while a mismatched pair is still on show (clicks are ignored). */
    public static boolean peeking() {
        int ms = metaAt(MemoryStatePayload.M_PEEK_MS);
        return ms > 0 && Util.getMillis() - syncedAt < ms;
    }

    /** Seconds elapsed, ticking locally between syncs so the clock doesn't stutter. */
    public static int elapsed() {
        int base = metaAt(MemoryStatePayload.M_ELAPSED);
        if (finished()) return base;
        return base + (int) ((Util.getMillis() - syncedAt) / 1000L);
    }

    public static List<Integer> scores() {
        List<Integer> out = new ArrayList<>();
        for (int i = MemoryStatePayload.M_SCORES; i < meta.size(); i++) out.add(meta.get(i));
        return out;
    }

    private static int metaAt(int index) {
        return index >= 0 && index < meta.size() ? meta.get(index) : 0;
    }
}
