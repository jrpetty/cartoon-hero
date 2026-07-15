package com.jrpetty.mobtrumps.client;

import java.util.List;

/**
 * Client-side snapshot of the current solo table battle, updated from the
 * server's {@link com.jrpetty.mobtrumps.BattleSyncPayload}. The battle screen
 * reads this every frame; {@link #changedAt()} marks the last phase change so
 * transitions can animate.
 */
public final class ClientBattle {

    private static volatile int phase;
    private static volatile String playerCardId = "";
    private static volatile String cpuCardId = "";
    private static volatile int playerCount;
    private static volatile int cpuCount;
    private static volatile int potCount;
    private static volatile int round;
    private static volatile int chosenStat = -1;
    private static volatile int chooser = 2;
    private static volatile int winner = 2;
    private static volatile int difficulty;
    private static volatile long changedAt;

    private ClientBattle() {
    }

    public static void set(int phase, String playerCardId, String cpuCardId, List<Integer> nums) {
        boolean phaseChanged = ClientBattle.phase != phase
                || !ClientBattle.playerCardId.equals(playerCardId)
                || !ClientBattle.cpuCardId.equals(cpuCardId);
        ClientBattle.phase = phase;
        ClientBattle.playerCardId = playerCardId == null ? "" : playerCardId;
        ClientBattle.cpuCardId = cpuCardId == null ? "" : cpuCardId;
        ClientBattle.playerCount = at(nums, 0);
        ClientBattle.cpuCount = at(nums, 1);
        ClientBattle.potCount = at(nums, 2);
        ClientBattle.round = at(nums, 3);
        ClientBattle.chosenStat = at(nums, 4);
        ClientBattle.chooser = at(nums, 5);
        ClientBattle.winner = at(nums, 6);
        ClientBattle.difficulty = at(nums, 7);
        if (phaseChanged) {
            changedAt = System.currentTimeMillis();
        }
    }

    private static int at(List<Integer> nums, int i) {
        return nums != null && i < nums.size() ? nums.get(i) : 0;
    }

    public static int phase() { return phase; }
    public static String playerCardId() { return playerCardId; }
    public static String cpuCardId() { return cpuCardId; }
    public static int playerCount() { return playerCount; }
    public static int cpuCount() { return cpuCount; }
    public static int potCount() { return potCount; }
    public static int round() { return round; }
    public static int chosenStat() { return chosenStat; }
    public static int chooser() { return chooser; }
    public static int winner() { return winner; }
    public static int difficulty() { return difficulty; }
    public static long changedAt() { return changedAt; }
}
