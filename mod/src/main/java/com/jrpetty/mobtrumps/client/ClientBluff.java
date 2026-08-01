package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BluffManager;
import com.jrpetty.mobtrumps.BluffSyncPayload;
import com.jrpetty.mobtrumps.game.Bluff;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of a Bluff table.
 *
 * <p>Deliberately thin. It knows your hand, everyone's hand <em>sizes</em>, and
 * the claim — and nothing whatever about what is in the pile or in anyone
 * else's hand, because the server never tells it. There is no clever local
 * model here to keep in step with the server, which is the point: the only way
 * to know whether a play was a lie is to call it.
 */
public final class ClientBluff {

    private static volatile int phase;
    private static volatile List<MobCard> hand = List.of();
    private static volatile int[] nums = new int[BluffSyncPayload.NUM_HAND_SIZES];
    private static volatile List<String> names = List.of();
    private static volatile List<String> log = List.of();
    private static volatile List<MobCard> reveal = List.of();
    private static volatile long changedAt;

    private ClientBluff() {
    }

    public static void set(BluffSyncPayload payload) {
        List<MobCard> cards = new ArrayList<>();
        for (String id : payload.hand()) {
            MobCard card = MobCards.byId(id);
            if (card != null) {
                cards.add(card);
            }
        }
        hand = List.copyOf(cards);
        int[] packed = new int[Math.max(BluffSyncPayload.NUM_HAND_SIZES, payload.nums().size())];
        for (int i = 0; i < payload.nums().size(); i++) {
            packed[i] = payload.nums().get(i);
        }
        nums = packed;
        phase = payload.phase();
        names = List.copyOf(payload.names());
        log = List.copyOf(payload.log());
        List<MobCard> shown = new ArrayList<>();
        for (String id : payload.reveal()) {
            MobCard card = MobCards.byId(id);
            if (card != null) {
                shown.add(card);
            }
        }
        reveal = List.copyOf(shown);
        changedAt = System.currentTimeMillis();
    }

    private static int num(int index) {
        int[] local = nums;
        return index >= 0 && index < local.length ? local[index] : 0;
    }

    public static int phase() {
        return phase;
    }

    public static List<MobCard> hand() {
        return hand;
    }

    public static List<String> log() {
        return log;
    }

    public static List<MobCard> reveal() {
        return reveal;
    }

    public static long changedAt() {
        return changedAt;
    }

    public static int mySeat() {
        return num(BluffSyncPayload.NUM_MY_SEAT);
    }

    public static int seats() {
        return Math.max(1, num(BluffSyncPayload.NUM_SEATS));
    }

    public static int turn() {
        return num(BluffSyncPayload.NUM_TURN);
    }

    public static int pile() {
        return num(BluffSyncPayload.NUM_PILE);
    }

    public static int lastSeat() {
        return num(BluffSyncPayload.NUM_LAST_SEAT);
    }

    public static int lastCount() {
        return num(BluffSyncPayload.NUM_LAST_COUNT);
    }

    public static int pendingOut() {
        return num(BluffSyncPayload.NUM_PENDING_OUT);
    }

    public static int winner() {
        return num(BluffSyncPayload.NUM_WINNER);
    }

    public static int burnt() {
        return num(BluffSyncPayload.NUM_BURNT);
    }

    public static int stakeIndex() {
        return num(BluffSyncPayload.NUM_STAKE);
    }

    public static int stake() {
        int index = Math.max(0, Math.min(BluffManager.STAKES.length - 1, stakeIndex()));
        return BluffManager.STAKES[index];
    }

    public static int handSize(int seat) {
        return num(BluffSyncPayload.NUM_HAND_SIZES + seat);
    }

    public static String seatName(int seat) {
        List<String> local = names;
        return seat >= 0 && seat < local.size() ? local.get(seat) : "Seat " + seat;
    }

    /** The round's claim, rebuilt from its three numbers. */
    public static Bluff.Claim claim() {
        return new Bluff.Claim(num(BluffSyncPayload.NUM_CLAIM_A),
                num(BluffSyncPayload.NUM_CLAIM_VALUE), num(BluffSyncPayload.NUM_CLAIM_B));
    }

    public static int revealChallenger() {
        return num(BluffSyncPayload.NUM_REVEAL_CHALLENGER);
    }

    public static int revealAccused() {
        return num(BluffSyncPayload.NUM_REVEAL_ACCUSED);
    }

    public static boolean revealWasLying() {
        return num(BluffSyncPayload.NUM_REVEAL_WAS_LYING) == 1;
    }

    public static int revealTaken() {
        return num(BluffSyncPayload.NUM_REVEAL_TAKEN);
    }

    public static boolean idle() {
        return phase == BluffManager.PHASE_IDLE;
    }

    public static boolean myTurn() {
        return phase == BluffManager.PHASE_YOUR_TURN;
    }

    public static boolean over() {
        return phase == BluffManager.PHASE_WON || phase == BluffManager.PHASE_LOST;
    }

    public static boolean won() {
        return phase == BluffManager.PHASE_WON;
    }

    /** True when the move in front of you may be doubted. */
    public static boolean canChallenge() {
        return myTurn() && lastSeat() >= 0 && lastSeat() != mySeat();
    }

    /** True when somebody is one unchallenged move from winning. */
    public static boolean atMatchPoint() {
        return pendingOut() >= 0;
    }
}
