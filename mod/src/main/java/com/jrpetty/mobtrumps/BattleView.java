package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.BattleSummary;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link BattleSyncPayload} every game mode sends, from one seat at
 * the table.
 *
 * <p>The CPU table, the campaign and the duel used to assemble the packet by
 * hand, slot by slot, and each filled a slightly different subset — the duel
 * never sent holo levels, the campaign never sent a series length. One builder
 * means a slot is filled everywhere or nowhere, and every reading of a card
 * (which side it is on, which level it is played at) happens in one place.
 *
 * <p>{@code seat} is whose eyes the view is through: {@code PLAYER} for the
 * person at a CPU table or on a mission, and either side in a duel, where the
 * challenger holds PLAYER and the challenged holds CPU.
 */
final class BattleView {

    private final int phase;
    private final Battle battle;
    private final Battle.Side seat;
    private Battle.RoundResult shown;
    private Battle.Side matchWinner;
    private int difficulty;
    private boolean pvp;
    private int myGames;
    private int oppGames;
    private int bestOf = 1;
    private int turnSeconds;
    private int mission;
    private boolean counts;
    private String label = "";
    private boolean summary;
    private int xp;
    private int emeralds;
    private int rating;
    private int ratingDelta;
    private String rank = "";
    private String note = "";
    private String note2 = "";

    private BattleView(int phase, Battle battle, Battle.Side seat) {
        this.phase = phase;
        this.battle = battle;
        this.seat = seat;
    }

    static BattleView of(int phase, Battle battle, Battle.Side seat) {
        return new BattleView(phase, battle, seat);
    }

    /** The round whose two cards are face up on the table. */
    BattleView shown(Battle.RoundResult round) {
        this.shown = round;
        return this;
    }

    /**
     * Who won the match, when that is not simply who won the battle — a
     * forfeit or a player leaving ends a match whose cards are still in play.
     */
    BattleView matchWinner(Battle.Side side) {
        this.matchWinner = side;
        return this;
    }

    BattleView difficulty(int difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    BattleView duel(int myGames, int oppGames, int bestOf, int turnSeconds) {
        this.pvp = true;
        this.myGames = myGames;
        this.oppGames = oppGames;
        this.bestOf = Math.max(1, bestOf);
        this.turnSeconds = turnSeconds;
        return this;
    }

    BattleView mission(int index) {
        this.mission = index;
        return this;
    }

    /** Whether the game feeds wins and awards. */
    BattleView counts(boolean counts) {
        this.counts = counts;
        return this;
    }

    BattleView label(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    /** Attach the end-of-game story — rounds, best run, best card — from the history. */
    BattleView summary() {
        this.summary = true;
        return this;
    }

    BattleView xp(int xp) {
        this.xp = Math.max(0, xp);
        return this;
    }

    BattleView emeralds(int emeralds) {
        this.emeralds = Math.max(0, emeralds);
        return this;
    }

    BattleView rated(int rating, int delta, String rank) {
        this.rating = rating;
        this.ratingDelta = delta;
        this.rank = rank == null ? "" : rank;
        return this;
    }

    BattleView notes(String note, String note2) {
        this.note = note == null ? "" : note;
        this.note2 = note2 == null ? "" : note2;
        return this;
    }

    private int relative(Battle.Side side) {
        if (side == null || side == Battle.Side.NONE) {
            return 2;
        }
        return side == seat ? 0 : 1;
    }

    BattleSyncPayload build() {
        boolean playerSeat = seat == Battle.Side.PLAYER;
        String myId = "";
        String oppId = "";
        int stat = -1;
        int chooser = 2;
        int winner = 2;
        int myLevel = 0;
        int oppLevel = 0;
        if (shown != null) {
            MobCard mine = playerSeat ? shown.playerCard() : shown.cpuCard();
            MobCard theirs = playerSeat ? shown.cpuCard() : shown.playerCard();
            myId = mine.id();
            oppId = theirs.id();
            myLevel = MobCards.levelOf(mine);
            oppLevel = MobCards.levelOf(theirs);
            stat = shown.stat().ordinal();
            chooser = relative(shown.chooser());
            winner = relative(shown.winner());
        } else {
            MobCard top = playerSeat ? battle.playerTopCard() : battle.cpuTopCard();
            if (top != null) {
                myId = top.id();
                myLevel = MobCards.levelOf(top);
            }
        }
        boolean over = phase == BattleSyncPayload.FINISHED || phase == BattleSyncPayload.GAME_OVER;
        if (over) {
            if (matchWinner != null) {
                winner = relative(matchWinner);
            } else if (battle.isFinished()) {
                winner = relative(battle.getWinner());
            }
        }
        Battle.Side coinSide = battle.lastCoin();
        int coin = coinSide == Battle.Side.NONE ? 0 : (coinSide == seat ? 1 : 2);

        BattleSummary story = summary ? BattleSummary.of(battle.history(), seat) : BattleSummary.EMPTY;
        List<MobCard> hand = playerSeat ? battle.playerHand() : battle.cpuHand();
        List<Battle.RoundResult> rounds = battle.history();
        int from = Math.max(0, rounds.size() - BattleSyncPayload.HISTORY_CAP);

        List<Integer> nums = new ArrayList<>(BattleSyncPayload.HEADER + hand.size() + rounds.size() - from);
        for (int i = 0; i < BattleSyncPayload.HEADER; i++) {
            nums.add(0);
        }
        nums.set(BattleSyncPayload.MY_COUNT, playerSeat ? battle.playerCardCount() : battle.cpuCardCount());
        nums.set(BattleSyncPayload.OPP_COUNT, playerSeat ? battle.cpuCardCount() : battle.playerCardCount());
        nums.set(BattleSyncPayload.POT, battle.potCount());
        nums.set(BattleSyncPayload.ROUND, battle.getRound());
        nums.set(BattleSyncPayload.STAT, stat);
        nums.set(BattleSyncPayload.CHOOSER, chooser);
        nums.set(BattleSyncPayload.WINNER, winner);
        nums.set(BattleSyncPayload.DIFFICULTY, difficulty);
        nums.set(BattleSyncPayload.PVP, pvp ? 1 : 0);
        nums.set(BattleSyncPayload.MY_GAMES, myGames);
        nums.set(BattleSyncPayload.OPP_GAMES, oppGames);
        nums.set(BattleSyncPayload.TURN_SECONDS, turnSeconds);
        nums.set(BattleSyncPayload.COIN, coin);
        nums.set(BattleSyncPayload.MISSION, mission);
        nums.set(BattleSyncPayload.MY_LEVEL, myLevel);
        nums.set(BattleSyncPayload.OPP_LEVEL, oppLevel);
        nums.set(BattleSyncPayload.BEST_OF, bestOf);
        nums.set(BattleSyncPayload.XP, xp);
        nums.set(BattleSyncPayload.ROUNDS_WON, story.won());
        nums.set(BattleSyncPayload.ROUNDS_LOST, story.lost());
        nums.set(BattleSyncPayload.ROUNDS_TIED, story.tied());
        nums.set(BattleSyncPayload.BEST_STREAK, story.bestStreak());
        nums.set(BattleSyncPayload.MVP_WINS, story.mvpWins());
        nums.set(BattleSyncPayload.MVP_LEVEL, MobCards.levelOf(story.mvp()));
        nums.set(BattleSyncPayload.EMERALDS, emeralds);
        nums.set(BattleSyncPayload.RATING, rating);
        nums.set(BattleSyncPayload.RATING_DELTA, ratingDelta);
        nums.set(BattleSyncPayload.COUNTS, counts ? 1 : 0);
        nums.set(BattleSyncPayload.HAND_COUNT, hand.size());
        nums.set(BattleSyncPayload.HISTORY_COUNT, rounds.size() - from);
        for (MobCard card : hand) {
            nums.add(MobCards.levelOf(card));
        }
        for (int i = from; i < rounds.size(); i++) {
            Battle.RoundResult r = rounds.get(i);
            nums.add(BattleSyncPayload.historyCode(r.stat().ordinal(),
                    relative(r.winner()), relative(r.chooser()) == 0 ? 0 : 1));
        }

        List<String> texts = new ArrayList<>(BattleSyncPayload.TEXT_HEADER + hand.size());
        texts.add(story.mvp() == null ? "" : story.mvp().id());
        texts.add(note);
        texts.add(note2);
        texts.add(rank);
        for (MobCard card : hand) {
            texts.add(card.id());
        }
        return new BattleSyncPayload(phase, myId, oppId, nums, label, texts);
    }

    /** The packet that closes the battle screen. */
    static BattleSyncPayload closed() {
        List<Integer> nums = new ArrayList<>(BattleSyncPayload.HEADER);
        for (int i = 0; i < BattleSyncPayload.HEADER; i++) {
            nums.add(0);
        }
        nums.set(BattleSyncPayload.STAT, -1);
        nums.set(BattleSyncPayload.CHOOSER, 2);
        nums.set(BattleSyncPayload.WINNER, 2);
        List<String> texts = new ArrayList<>(List.of("", "", "", ""));
        return new BattleSyncPayload(BattleSyncPayload.CLOSED, "", "", nums, "", texts);
    }
}
