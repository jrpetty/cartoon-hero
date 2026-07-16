package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BattleSyncPayload;

import java.util.List;

/**
 * Client-side snapshot of the current table battle (vs CPU or a live duel),
 * updated from {@link BattleSyncPayload}. The battle screen reads this every
 * frame; {@link #changedAt()} marks the last phase change so transitions can
 * animate.
 *
 * <p>In a duel the server resolves a round and immediately prompts the next
 * turn, so the "next" state would overwrite the reveal before you ever see the
 * flip. To fix that, a post-result state in a PvP game is <em>held</em> for
 * {@link #REVEAL_HOLD_MS} — buffered in {@code pending} and promoted by
 * {@link #poll()} once the reveal has played. CPU battles advance on your own
 * click, so they never buffer and stay instant.
 */
public final class ClientBattle {

    private static final long REVEAL_HOLD_MS = 1900L;

    // current (displayed) state
    private static volatile int phase;
    private static volatile String myCardId = "";
    private static volatile String oppCardId = "";
    private static volatile int myCount;
    private static volatile int oppCount;
    private static volatile int potCount;
    private static volatile int round;
    private static volatile int chosenStat = -1;
    private static volatile int chooser = 2;
    private static volatile int winner = 2;
    private static volatile int difficulty;
    private static volatile int pvp;
    private static volatile int myGames;
    private static volatile int oppGames;
    private static volatile String label = "";
    private static volatile long changedAt;

    private record Snapshot(int phase, String my, String opp, List<Integer> nums, String label) {
    }

    private static volatile Snapshot pending;

    private ClientBattle() {
    }

    public static void set(int phase, String my, String opp, List<Integer> nums, String label) {
        Snapshot s = new Snapshot(phase, my, opp, nums, label);
        long now = System.currentTimeMillis();
        boolean holding = ClientBattle.phase == BattleSyncPayload.RESULT
                && pvp == 1
                && now - changedAt < REVEAL_HOLD_MS
                && phase != BattleSyncPayload.RESULT
                && phase != BattleSyncPayload.CLOSED;
        if (holding) {
            pending = s; // let the flip/result play before the next turn lands
        } else {
            apply(s);
        }
    }

    /** Called every frame by the screen: promote a held state once the reveal has shown. */
    public static void poll() {
        Snapshot p = pending;
        if (p == null) {
            return;
        }
        if (phase != BattleSyncPayload.RESULT
                || System.currentTimeMillis() - changedAt >= REVEAL_HOLD_MS) {
            pending = null;
            apply(p);
        }
    }

    private static void apply(Snapshot s) {
        boolean changed = phase != s.phase()
                || !myCardId.equals(nz(s.my())) || !oppCardId.equals(nz(s.opp()));
        phase = s.phase();
        myCardId = nz(s.my());
        oppCardId = nz(s.opp());
        List<Integer> n = s.nums();
        myCount = at(n, 0);
        oppCount = at(n, 1);
        potCount = at(n, 2);
        round = at(n, 3);
        chosenStat = at(n, 4);
        chooser = at(n, 5);
        winner = at(n, 6);
        difficulty = at(n, 7);
        pvp = at(n, 8);
        myGames = at(n, 9);
        oppGames = at(n, 10);
        label = nz(s.label());
        if (changed) {
            changedAt = System.currentTimeMillis();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int at(List<Integer> nums, int i) {
        return nums != null && i < nums.size() ? nums.get(i) : 0;
    }

    public static int phase() { return phase; }
    public static String playerCardId() { return myCardId; }
    public static String cpuCardId() { return oppCardId; }
    public static int playerCount() { return myCount; }
    public static int cpuCount() { return oppCount; }
    public static int potCount() { return potCount; }
    public static int round() { return round; }
    public static int chosenStat() { return chosenStat; }
    public static int chooser() { return chooser; }
    public static int winner() { return winner; }
    public static int difficulty() { return difficulty; }
    public static boolean isPvp() { return pvp == 1; }
    public static int myGames() { return myGames; }
    public static int oppGames() { return oppGames; }
    public static String label() { return label; }
    public static long changedAt() { return changedAt; }
}
