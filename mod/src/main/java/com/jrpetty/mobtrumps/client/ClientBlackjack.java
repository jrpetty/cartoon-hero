package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BlackjackSyncPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of the Twenty-One table. Display only — the hand, the shoe
 * and every decision live on the server, and this holds just enough to draw them.
 */
public final class ClientBlackjack {

    /** One turned card: which stat was called, what came up, what it was worth. */
    public record Draw(int stat, String mobId, int value, int total) {
    }

    private static volatile int phase = BlackjackSyncPayload.PHASE_DONE;
    private static volatile int result;
    private static volatile int playerTotal;
    private static volatile int dealerTotal;
    private static volatile int drawsTaken;
    private static volatile int fragments;
    private static volatile int stake;
    private static volatile int stakeIndex;
    private static volatile List<Draw> player = List.of();
    private static volatile List<Draw> dealer = List.of();
    private static volatile long changedAt;

    private ClientBlackjack() {
    }

    public static void set(BlackjackSyncPayload payload) {
        int previous = playerTotal;
        int previousDealer = dealer.size();
        phase = payload.phase();
        result = payload.result();
        playerTotal = at(payload.nums(), 0);
        dealerTotal = at(payload.nums(), 1);
        drawsTaken = at(payload.nums(), 2);
        fragments = at(payload.nums(), 3);
        stake = at(payload.nums(), 4);
        stakeIndex = at(payload.nums(), 5);
        player = decode(payload.player());
        dealer = decode(payload.dealer());
        if (playerTotal != previous || dealer.size() != previousDealer) {
            changedAt = System.currentTimeMillis();
        }
    }

    private static List<Draw> decode(List<String> raw) {
        List<Draw> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            String[] bits = line.split("\\|", 4);
            if (bits.length != 4) {
                continue; // a malformed line is dropped, never allowed to throw mid-render
            }
            try {
                out.add(new Draw(Integer.parseInt(bits[0]), bits[1],
                        Integer.parseInt(bits[2]), Integer.parseInt(bits[3])));
            } catch (NumberFormatException ignored) {
                // same reasoning: the table is cosmetic here, the server is the truth
            }
        }
        return List.copyOf(out);
    }

    private static int at(List<Integer> nums, int i) {
        return nums != null && i < nums.size() ? nums.get(i) : 0;
    }

    public static int phase() {
        return phase;
    }

    public static int result() {
        return result;
    }

    public static int playerTotal() {
        return playerTotal;
    }

    public static int dealerTotal() {
        return dealerTotal;
    }

    public static int fragments() {
        return fragments;
    }

    public static int stake() {
        return stake;
    }

    /** Which rung of the stake ladder is selected. */
    public static int stakeIndex() {
        return stakeIndex;
    }

    public static List<Draw> playerDraws() {
        return player;
    }

    public static List<Draw> dealerDraws() {
        return dealer;
    }

    public static long changedAt() {
        return changedAt;
    }

    /** How many cards the player has taken this hand. */
    public static int drawsTaken() {
        return drawsTaken;
    }

    /** True when there is a hand in progress waiting on the player. */
    public static boolean inHand() {
        return phase == BlackjackSyncPayload.PHASE_PLAYER;
    }
}
