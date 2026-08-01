package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mob Bluff — Cheat, played with mob cards and claims instead of ranks.
 *
 * <p>A claim is turned up for the round: "these are all hostile", "these all
 * have Rarity above 5". On your turn you put one to three cards face down and
 * assert they all match it, or you challenge the play before yours. A challenge
 * turns the last play over: if every card matched, the challenger eats the pile;
 * if even one did not, the liar does. First hand emptied wins.
 *
 * <p><b>Why the claim is drawn rather than chosen.</b> If players picked their
 * own claim they would simply describe cards they actually hold, and nobody
 * would ever have to lie — which is the whole game. A claim fixed for the round
 * is what forces the bluff: you can follow it honestly while your matching cards
 * last, and after that you are lying or you are stuck.
 *
 * <p><b>Why only some claims are allowed.</b> A claim that matches three cards
 * in the deck means everyone is always lying, so challenging is free and the
 * game collapses. One that matches seventy means nobody ever needs to. Only
 * claims landing between {@value #MIN_RATE} and {@value #MAX_RATE} of the deck
 * are dealt, which is the band where a hand of {@value #HAND_SIZE} holds a few
 * honest cards and then runs out.
 *
 * <p>Pure logic, no Minecraft — the server runs this and the client only ever
 * sees what it is told, which is what keeps face-down cards face down.
 */
public final class Bluff {

    /** Cards dealt to each player. */
    public static final int HAND_SIZE = 8;
    /** Most cards that may go down in one play. */
    public static final int MAX_PLAY = 3;
    /** Seats, including yours. */
    public static final int MIN_SEATS = 2;
    public static final int MAX_SEATS = 4;

    /** A claim must match at least this share of the deck to be dealt. */
    public static final double MIN_RATE = 0.25;
    /** ...and at most this share. */
    public static final double MAX_RATE = 0.55;

    /**
     * Hard stop on the number of moves, after which the smallest hand wins.
     *
     * <p>Hands only grow when somebody eats a pile, so a table that challenges
     * constantly can pass the same few cards round forever. In twenty thousand
     * simulated two-player games one in a thousand was still going after six
     * hundred moves. That is rare enough never to be seen and certain enough to
     * need an ending, and a stuck game should be won by whoever got closest.
     */
    public static final int MAX_MOVES = 600;

    /**
     * One round's claim: a single question from the Guess Who catalogue, or two
     * of the same kind joined by "or".
     *
     * <p>The "or" exists because the catalogue on its own is too lopsided for
     * this game. Its categories top out at fourteen cards in eighty-one and most
     * of its traits are narrower still — fine for Guess Who, where a question
     * that splits 16/65 is a good question when it lands, and useless here,
     * where it would mean everybody lies every turn. Joining two of them lifts
     * the flavoured claims into the playable band instead of leaving the game
     * to run on nothing but numbers.
     *
     * @param a      index into {@link GuessQuestion#TEMPLATES}
     * @param value  the threshold, for a numeric claim; ignored otherwise
     * @param b      a second template OR-ed with the first, or -1 for none
     */
    public record Claim(int a, int value, int b) {

        public boolean matches(MobCard card) {
            GuessQuestion.Template ta = GuessQuestion.TEMPLATES.get(a);
            if (GuessQuestion.ask(ta, value, card)) {
                return true;
            }
            return b >= 0 && GuessQuestion.ask(GuessQuestion.TEMPLATES.get(b), 0, card);
        }

        /**
         * How the claim reads on screen.
         *
         * <p>It stays in the catalogue's question form — "Is it hostile?" — and
         * the screen frames it as the question every card played must answer
         * YES to. Rewriting each question into a statement meant guessing at
         * English ("Does it fly?" came out as "flys"), and the question form is
         * already the one players learned in Guess Who.
         */
        public String text() {
            GuessQuestion.Template ta = GuessQuestion.TEMPLATES.get(a);
            if (b < 0) {
                return ta.text(value);
            }
            // both halves open the same way, so the second sheds its opener:
            // "Does it fly?" + "Does it burn in daylight?"
            //   -> "Does it fly or burn in daylight?"
            GuessQuestion.Template tb = GuessQuestion.TEMPLATES.get(b);
            String open = opener(ta);
            String rest = tb.text(0).substring(open.length()).replace("?", "");
            return ta.text(value).replace("?", "") + " or " + rest + "?";
        }

        /** How many cards in the deck match — the honest-play rate. */
        public int deckCount() {
            int n = 0;
            for (MobCard card : MobCards.ALL) {
                if (matches(card)) {
                    n++;
                }
            }
            return n;
        }
    }

    /**
     * The interrogative a question opens with — "Is it ", "Does it ", "Can you ".
     *
     * <p>Two questions can only be joined by "or" when they open the same way,
     * which is what lets the second one drop its opener and read as one
     * sentence. Empty when the phrasing is not one this recognises, which stops
     * that question being paired at all rather than producing nonsense.
     */
    static String opener(GuessQuestion.Template t) {
        String q = t.text(0);
        for (String open : new String[]{"Is its ", "Is it ", "Does it ", "Can you "}) {
            if (q.startsWith(open)) {
                return open;
            }
        }
        return "";
    }

    /** Every claim balanced enough to play a round on. Built once. */
    public static final List<Claim> CLAIMS;

    static {
        List<Claim> pool = new ArrayList<>();
        int n = GuessQuestion.TEMPLATES.size();
        int deck = MobCards.ALL.size();
        // single claims, numeric and otherwise
        for (int i = 0; i < n; i++) {
            GuessQuestion.Template t = GuessQuestion.TEMPLATES.get(i);
            if (t.needsValue()) {
                for (int v = t.minValue(); v <= t.maxValue(); v++) {
                    add(pool, new Claim(i, v, -1), deck);
                }
            } else {
                add(pool, new Claim(i, 0, -1), deck);
            }
        }
        // "or" pairs. Same kind AND same opening words, so the second half can
        // shed its opener and the whole thing reads as one sentence. Mixing a
        // category with a trait gave "a farm animal or stands on two legs".
        for (int i = 0; i < n; i++) {
            GuessQuestion.Template ti = GuessQuestion.TEMPLATES.get(i);
            if (ti.needsValue() || opener(ti).isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                GuessQuestion.Template tj = GuessQuestion.TEMPLATES.get(j);
                if (tj.needsValue() || tj.kind() != ti.kind()
                        || !opener(tj).equals(opener(ti))) {
                    continue;
                }
                add(pool, new Claim(i, 0, j), deck);
            }
        }
        CLAIMS = List.copyOf(pool);
    }

    private static void add(List<Claim> pool, Claim claim, int deck) {
        double rate = claim.deckCount() / (double) deck;
        if (rate >= MIN_RATE && rate <= MAX_RATE) {
            pool.add(claim);
        }
    }

    /** What a finished challenge did, for the log and the screen. */
    public record Reveal(int challenger, int accused, List<MobCard> cards, Claim claim,
                         boolean wasLying, int pileTaken) {

        /** The seat that ate the pile. */
        public int loser() {
            return wasLying ? accused : challenger;
        }
    }

    private final List<List<MobCard>> hands = new ArrayList<>();
    private final List<MobCard> pile = new ArrayList<>();
    private final Random random;
    private final int seats;

    private Claim claim;
    private int turn;
    /** Seat that made the play now sitting on top of the pile, or -1. */
    private int lastSeat = -1;
    /** How many cards that play was. */
    private int lastCount;
    /** Seat that has emptied its hand and survives if nobody challenges. */
    private int pendingOut = -1;
    private int winner = -1;
    private int moves;
    private Reveal lastReveal;
    private final List<String> log = new ArrayList<>();

    public Bluff(int seats, Random random) {
        this.seats = Math.max(MIN_SEATS, Math.min(MAX_SEATS, seats));
        this.random = random;
        deal();
    }

    private void deal() {
        hands.clear();
        pile.clear();
        List<MobCard> deck = new ArrayList<>(MobCards.ALL);
        Collections.shuffle(deck, random);
        int at = 0;
        for (int s = 0; s < seats; s++) {
            List<MobCard> hand = new ArrayList<>(deck.subList(at, at + HAND_SIZE));
            at += HAND_SIZE;
            hand.sort(java.util.Comparator.comparing(MobCard::displayName));
            hands.add(hand);
        }
        claim = CLAIMS.get(random.nextInt(CLAIMS.size()));
        turn = random.nextInt(seats);
        lastSeat = -1;
        lastCount = 0;
        pendingOut = -1;
        log.add("Claim: " + claim.text());
    }

    // ---- state, all read-only from outside ------------------------------

    public int seats() {
        return seats;
    }

    public Claim claim() {
        return claim;
    }

    public int turn() {
        return turn;
    }

    public int winner() {
        return winner;
    }

    public boolean done() {
        return winner >= 0;
    }

    public int pileSize() {
        return pile.size();
    }

    public int handSize(int seat) {
        return hands.get(seat).size();
    }

    /** The cards in a seat's hand. Only ever handed to that seat's own client. */
    public List<MobCard> hand(int seat) {
        return List.copyOf(hands.get(seat));
    }

    public int lastSeat() {
        return lastSeat;
    }

    public int lastCount() {
        return lastCount;
    }

    /** The seat one challenge away from winning, or -1. */
    public int pendingOut() {
        return pendingOut;
    }

    public Reveal lastReveal() {
        return lastReveal;
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    /** True when the seat to act is allowed to challenge — there is a play to doubt. */
    public boolean canChallenge() {
        return !done() && lastSeat >= 0 && lastSeat != turn;
    }

    // ---- the two moves ---------------------------------------------------

    /**
     * Put cards face down, asserting they all match the round's claim.
     *
     * @param seat    who is playing; must be the seat to act
     * @param picks   indices into that seat's hand, 1..{@value #MAX_PLAY} of them
     * @return true if the play was legal and taken
     */
    public boolean play(int seat, List<Integer> picks) {
        if (done() || seat != turn || picks.isEmpty() || picks.size() > MAX_PLAY) {
            return false;
        }
        List<MobCard> hand = hands.get(seat);
        // distinct, in range — a repeated index would play one card twice
        List<Integer> sorted = new ArrayList<>(picks);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            int p = sorted.get(i);
            if (p < 0 || p >= hand.size() || (i > 0 && p == sorted.get(i - 1))) {
                return false;
            }
        }
        // when someone has put their last cards down, the only two moves are
        // challenge and let them go — playing on is not one of them
        if (pendingOut >= 0) {
            return false;
        }
        List<MobCard> played = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            played.add(0, hand.remove((int) sorted.get(i)));
        }
        pile.addAll(played);
        lastSeat = seat;
        lastCount = played.size();
        log.add("Seat " + seat + " played " + played.size()
                + (played.size() == 1 ? " card" : " cards"));
        if (hand.isEmpty()) {
            // they are out unless the next seat catches them
            pendingOut = seat;
        }
        turn = (turn + 1) % seats;
        countMove();
        return true;
    }

    /**
     * Doubt the play on top of the pile.
     *
     * <p>Whoever is wrong takes every card in the pile into their hand — the
     * punishment is the pile, so a long unchallenged round is worth more and
     * risking a challenge late is worth more than risking it early.
     */
    public boolean challenge(int seat) {
        if (done() || seat != turn || lastSeat < 0 || lastSeat == seat) {
            return false;
        }
        List<MobCard> shown = new ArrayList<>(pile.subList(pile.size() - lastCount, pile.size()));
        boolean lying = false;
        for (MobCard card : shown) {
            if (!claim.matches(card)) {
                lying = true;
                break;
            }
        }
        int loser = lying ? lastSeat : seat;
        lastReveal = new Reveal(seat, lastSeat, List.copyOf(shown), claim, lying, pile.size());
        hands.get(loser).addAll(pile);
        hands.get(loser).sort(java.util.Comparator.comparing(MobCard::displayName));
        pile.clear();
        log.add(lying
                ? "Seat " + seat + " caught seat " + lastSeat + " — seat " + loser + " takes the pile"
                : "Seat " + seat + " was wrong — takes the pile");
        // Surviving a challenge on your last cards wins the game. Without this
        // an honest player who put their final card down and got doubted would
        // be left holding nothing, no longer pending, and unable to ever play
        // again — the challenger would have been punished and the winner would
        // have been erased by the same move.
        if (!lying && hands.get(lastSeat).isEmpty()) {
            winner = lastSeat;
            log.add("Seat " + winner + " was telling the truth on their last card — they win.");
            pendingOut = -1;
            lastSeat = -1;
            lastCount = 0;
            return true;
        }
        pendingOut = -1;
        lastSeat = -1;
        lastCount = 0;
        // a fresh claim, and the seat that ate the pile leads
        claim = CLAIMS.get(random.nextInt(CLAIMS.size()));
        log.add("Claim: " + claim.text());
        turn = loser;
        countMove();
        return true;
    }

    /**
     * Let a pending exit stand: the seat to act declines to challenge and the
     * player who put their last cards down wins.
     */
    public boolean passOnExit(int seat) {
        if (done() || seat != turn || pendingOut < 0) {
            return false;
        }
        winner = pendingOut;
        pendingOut = -1;
        log.add("Nobody challenged — seat " + winner + " is out.");
        return true;
    }

    /** Moves made so far, for the safety stop. */
    public int moves() {
        return moves;
    }

    /**
     * Tick the move counter and, at the cap, award the game to the smallest
     * hand. Ties go to the earliest seat, which is arbitrary but has to be
     * something; a shared win is not a state the rest of the mod can pay out.
     */
    private void countMove() {
        if (++moves < MAX_MOVES || winner >= 0) {
            return;
        }
        int best = 0;
        for (int s = 1; s < seats; s++) {
            if (hands.get(s).size() < hands.get(best).size()) {
                best = s;
            }
        }
        winner = best;
        log.add("Move limit reached — seat " + best + " wins on the smallest hand.");
    }
}
