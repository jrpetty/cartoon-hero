package com.jrpetty.mobtrumps.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One finished game, told from one side of the table: how the rounds went,
 * the longest run of rounds won, and the card that carried the game.
 *
 * <p>The MVP is the card that won the most rounds for this side. When two
 * cards won the same number, it is the one that got there first — the card
 * that set the mark, not the one that caught up with it. A side that never won
 * a round has no MVP.
 *
 * @param won        rounds this side won
 * @param lost       rounds the other side won
 * @param tied       drawn rounds, which sent both cards to the pot
 * @param bestStreak the most rounds this side won in a row; a draw ends a run
 * @param mvp        the card that won the most rounds, or null
 * @param mvpWins    how many rounds that card won
 */
public record BattleSummary(int won, int lost, int tied, int bestStreak, MobCard mvp, int mvpWins) {

    public static final BattleSummary EMPTY = new BattleSummary(0, 0, 0, 0, null, 0);

    /** Summarise {@code history} for {@code side} (PLAYER or CPU). */
    public static BattleSummary of(List<Battle.RoundResult> history, Battle.Side side) {
        if (history == null || side == Battle.Side.NONE) {
            return EMPTY;
        }
        int won = 0;
        int lost = 0;
        int tied = 0;
        int streak = 0;
        int bestStreak = 0;
        Map<String, Integer> wins = new HashMap<>();
        MobCard mvp = null;
        int mvpWins = 0;
        for (Battle.RoundResult round : history) {
            if (round.winner() == Battle.Side.NONE) {
                tied++;
                streak = 0;
            } else if (round.winner() == side) {
                won++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
                MobCard mine = side == Battle.Side.PLAYER ? round.playerCard() : round.cpuCard();
                int count = wins.merge(mine.id(), 1, Integer::sum);
                // strictly greater: a card only takes the title by passing the holder
                if (count > mvpWins) {
                    mvp = mine;
                    mvpWins = count;
                }
            } else {
                lost++;
                streak = 0;
            }
        }
        return new BattleSummary(won, lost, tied, bestStreak, mvp, mvpWins);
    }

    public int rounds() {
        return won + lost + tied;
    }
}
