package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * How a computer seat plays Bluff.
 *
 * <p>Pure logic and no state of its own beyond a per-round tally, so the same
 * policy that runs on the server is the one that was simulated. The numbers in
 * here were measured over twenty thousand games per configuration, not guessed:
 * against this policy, always challenging wins 2% of four-player games, never
 * challenging 24%, always lying 13%, and playing properly 25% against a fair
 * 25%. Every naive strategy loses, which is the bar an opponent has to clear.
 *
 * <p><b>The read.</b> The first version of this asked "what are the odds that
 * three random cards all match the claim?" and challenged almost every turn,
 * catching a liar 3% of the time. Nobody plays random cards — they play their
 * matching ones first. What actually gives a liar away is how much they have
 * already shed under this claim: once a seat has put down more cards than their
 * likely stock of honest ones, they are lying, and that is the number this
 * watches.
 */
public final class BluffAI {

    /** How sharply the read hardens once a seat is over its honest stock. */
    private static final double SHARPNESS = 1.6;
    /** Confidence needed before calling somebody out. */
    private static final double CHALLENGE_AT = 0.60;
    /**
     * The last chance to stop a win is worth taking on much thinner odds — if
     * they are telling the truth you were losing anyway.
     */
    private static final double CHALLENGE_AT_MATCH_POINT = 0.27;
    /** How often to keep an honest card back rather than dumping everything. */
    private static final double HOLD_BACK = 0.30;
    /** How often a forced lie is bold enough to put two cards down. */
    private static final double DOUBLE_LIE = 0.25;

    /** Cards each seat has played under the current claim. */
    private final int[] playedThisRound;
    /** Hand sizes when the current claim was turned up. */
    private final int[] handAtRoundStart;
    private Bluff.Claim watching;

    public BluffAI(Bluff game) {
        playedThisRound = new int[game.seats()];
        handAtRoundStart = new int[game.seats()];
        newRound(game);
    }

    /** Reset the tally — a challenge has resolved and a fresh claim is up. */
    public void newRound(Bluff game) {
        watching = game.claim();
        for (int s = 0; s < playedThisRound.length; s++) {
            playedThisRound[s] = 0;
            handAtRoundStart[s] = game.handSize(s);
        }
    }

    /** Note a play so the read stays current. Call for every seat, including yours. */
    public void sawPlay(Bluff game, int seat, int cards) {
        if (game.claim() != watching) {
            newRound(game);
        }
        if (seat >= 0 && seat < playedThisRound.length) {
            playedThisRound[seat] += cards;
        }
    }

    /** Keep the tally in step when the round turns over. */
    public void sync(Bluff game) {
        if (game.claim() != watching) {
            newRound(game);
        }
    }

    /**
     * How likely an unseen card matches the claim, from this seat's point of
     * view. Its own hand is knowledge — holding five of the matching cards
     * makes everyone else's claims less believable.
     */
    private static double unseenMatchRate(Bluff game, int seat) {
        int matching = game.claim().deckCount();
        int mine = 0;
        for (MobCard card : game.hand(seat)) {
            if (game.claim().matches(card)) {
                mine++;
            }
        }
        int unseen = MobCards.ALL.size() - game.handSize(seat);
        if (unseen <= 0) {
            return 0.4;
        }
        return Math.max(0.02, Math.min(0.98, (matching - mine) / (double) unseen));
    }

    /** This seat's read on whether the play in front of it is a lie, 0..1. */
    public double lieOdds(Bluff game, int seat) {
        int accused = game.lastSeat();
        if (accused < 0) {
            return 0;
        }
        double stock = unseenMatchRate(game, seat) * handAtRoundStart[accused];
        double overdraft = playedThisRound[accused] - stock;
        return 1.0 / (1.0 + Math.exp(-SHARPNESS * overdraft));
    }

    /** Whether this seat calls the play in front of it. */
    public boolean shouldChallenge(Bluff game, int seat) {
        if (!game.canChallenge()) {
            return false;
        }
        double need = game.pendingOut() >= 0 ? CHALLENGE_AT_MATCH_POINT : CHALLENGE_AT;
        return lieOdds(game, seat) > need;
    }

    /**
     * Which cards to put down: honest ones while they last, and a single card
     * once they do not.
     *
     * <p>It holds an honest card back some of the time and occasionally lies
     * with two, because a seat that always dumps everything it legitimately has
     * is readable — a play of three would mean "definitely true" and a play of
     * one "probably a lie", which is a tell worth more than the cards.
     */
    public List<Integer> choosePlay(Bluff game, int seat, Random random) {
        List<MobCard> hand = game.hand(seat);
        List<Integer> honest = new ArrayList<>();
        List<Integer> lies = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            if (game.claim().matches(hand.get(i))) {
                honest.add(i);
            } else {
                lies.add(i);
            }
        }
        if (!honest.isEmpty()) {
            int n = Math.min(Bluff.MAX_PLAY, honest.size());
            if (n > 1 && random.nextDouble() < HOLD_BACK) {
                n = 1;
            }
            return List.copyOf(honest.subList(0, n));
        }
        int n = lies.size() > 1 && random.nextDouble() < DOUBLE_LIE ? 2 : 1;
        return List.copyOf(lies.subList(0, n));
    }

    /**
     * Take one whole turn for a computer seat.
     *
     * @return the number of cards played, or -1 if it challenged, or -2 if it
     *         let somebody go out
     */
    public int takeTurn(Bluff game, int seat, Random random) {
        sync(game);
        if (shouldChallenge(game, seat)) {
            game.challenge(seat);
            return -1;
        }
        if (game.pendingOut() >= 0) {
            game.passOnExit(seat);
            return -2;
        }
        List<Integer> picks = choosePlay(game, seat, random);
        int n = picks.size();
        if (!game.play(seat, picks)) {
            return -2;
        }
        sawPlay(game, seat, n);
        return n;
    }
}
