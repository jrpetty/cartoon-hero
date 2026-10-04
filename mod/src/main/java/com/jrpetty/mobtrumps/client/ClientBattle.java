package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BattleSyncPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side snapshot of the current table battle (vs CPU, a mission or a
 * live duel), updated from {@link BattleSyncPayload}. The battle screen reads
 * this every frame; {@link #changedAt()} marks the last phase change so
 * transitions can animate.
 *
 * <p>In a duel the server resolves a round and immediately prompts the next
 * turn, so the "next" state would overwrite the reveal before you ever see the
 * flip. To fix that, a post-result state in a PvP game is <em>held</em> for
 * {@link #REVEAL_HOLD_MS} — buffered in {@code pending} and promoted by
 * {@link #poll()} once the reveal has played. CPU battles advance on your own
 * click (or the auto-continue), so they never buffer and stay instant.
 */
public final class ClientBattle {

    private static final long REVEAL_HOLD_MS = 1900L;

    /** Everything one packet said, already decoded. */
    private record Snapshot(int phase, String my, String opp, List<Integer> nums, String label,
                            List<String> texts) {
    }

    private static volatile Snapshot shown = new Snapshot(BattleSyncPayload.CLOSED, "", "",
            List.of(), "", List.of());
    private static volatile Snapshot pending;
    private static volatile long changedAt;

    // transient emote bubble
    private static volatile int emoteSide = -1;
    private static volatile String emoteText = "";
    private static volatile long emoteAt;

    private ClientBattle() {
    }

    public static void set(int phase, String my, String opp, List<Integer> nums, String label,
                           List<String> texts) {
        Snapshot s = new Snapshot(phase, nz(my), nz(opp), nums == null ? List.of() : nums, nz(label),
                texts == null ? List.of() : texts);
        long now = System.currentTimeMillis();
        boolean holding = shown.phase() == BattleSyncPayload.RESULT
                && isPvp()
                && now - changedAt < REVEAL_HOLD_MS
                && phase != BattleSyncPayload.RESULT
                && phase != BattleSyncPayload.CLOSED;
        if (holding) {
            pending = s; // let the flip/result play before the next turn lands
        } else {
            pending = null; // anything held is out of date now
            apply(s);
        }
    }

    /** Called every frame by the screen: promote a held state once the reveal has shown. */
    public static void poll() {
        Snapshot p = pending;
        if (p == null) {
            return;
        }
        if (shown.phase() != BattleSyncPayload.RESULT
                || System.currentTimeMillis() - changedAt >= REVEAL_HOLD_MS) {
            pending = null;
            apply(p);
        }
    }

    private static void apply(Snapshot s) {
        Snapshot was = shown;
        boolean changed = was.phase() != s.phase() || !was.my().equals(s.my()) || !was.opp().equals(s.opp())
                || num(was, BattleSyncPayload.ROUND) != num(s, BattleSyncPayload.ROUND);
        shown = s;
        if (changed) {
            changedAt = System.currentTimeMillis();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int num(Snapshot s, int i) {
        return i >= 0 && i < s.nums().size() ? s.nums().get(i) : 0;
    }

    private static int num(int i) {
        return num(shown, i);
    }

    private static String text(int i) {
        List<String> t = shown.texts();
        return i >= 0 && i < t.size() ? nz(t.get(i)) : "";
    }

    // --- emotes ------------------------------------------------------------

    /** Show an emote bubble: side 0 = my card, 1 = opponent's card. */
    public static void setEmote(int side, String text) {
        emoteSide = side;
        emoteText = text == null ? "" : text;
        emoteAt = System.currentTimeMillis();
    }

    public static int emoteSide() { return emoteSide; }
    public static String emoteText() { return emoteText; }
    public static long emoteAt() { return emoteAt; }

    // --- the table ---------------------------------------------------------

    public static int phase() { return shown.phase(); }
    public static String playerCardId() { return shown.my(); }
    public static String cpuCardId() { return shown.opp(); }
    public static String label() { return shown.label(); }
    public static long changedAt() { return changedAt; }

    public static int playerCount() { return num(BattleSyncPayload.MY_COUNT); }
    public static int cpuCount() { return num(BattleSyncPayload.OPP_COUNT); }
    public static int potCount() { return num(BattleSyncPayload.POT); }
    public static int round() { return num(BattleSyncPayload.ROUND); }
    public static int chosenStat() { return shown.nums().size() > BattleSyncPayload.STAT ? num(BattleSyncPayload.STAT) : -1; }
    public static int chooser() { return num(BattleSyncPayload.CHOOSER); }
    public static int winner() { return num(BattleSyncPayload.WINNER); }
    public static int difficulty() { return num(BattleSyncPayload.DIFFICULTY); }
    public static boolean isPvp() { return num(BattleSyncPayload.PVP) == 1; }
    public static int myGames() { return num(BattleSyncPayload.MY_GAMES); }
    public static int oppGames() { return num(BattleSyncPayload.OPP_GAMES); }
    public static int turnSeconds() { return num(BattleSyncPayload.TURN_SECONDS); }

    /** Coin flip that settled a drawn round: 0 none, 1 you pick next, 2 they do. */
    public static int coin() { return num(BattleSyncPayload.COIN); }

    /** The campaign mission this game belongs to, or 0 if it is not a mission. */
    public static int campaignMission() { return num(BattleSyncPayload.MISSION); }

    /** Games in the match: 1 for a single game, 3 or 5 for a series. */
    public static int bestOf() { return Math.max(1, num(BattleSyncPayload.BEST_OF)); }

    /** Rounds before the game is called on cards held, 0 for none. */
    public static int roundLimit() { return num(BattleSyncPayload.ROUND_LIMIT); }

    /** The game was settled at the limit on cards held. */
    public static boolean onTime() { return num(BattleSyncPayload.ON_TIME) == 1; }

    /** Whether this game feeds wins and awards. */
    public static boolean counts() { return num(BattleSyncPayload.COUNTS) == 1; }

    /**
     * The card each side actually has in play, upgraded to the print they are
     * fielding. Looking a card up by id alone gives the base print, which is a
     * different card with different numbers whenever a level is in play — and
     * the mismatch reads as the round having been decided wrongly.
     */
    public static MobCard myCard() {
        return upgrade(MobCards.byId(shown.my()), num(BattleSyncPayload.MY_LEVEL));
    }

    public static MobCard oppCard() {
        return upgrade(MobCards.byId(shown.opp()), num(BattleSyncPayload.OPP_LEVEL));
    }

    private static MobCard upgrade(MobCard card, int level) {
        return card == null || level <= 0 ? card : card.upgraded(level);
    }

    /**
     * A card as it is held: the base print and the holo level it plays at, so
     * it can be drawn in its proper frame with its boosts marked, not just
     * with the right numbers.
     */
    public record Held(MobCard base, int level) {
        public MobCard card() {
            return upgrade(base, level);
        }
    }

    public static Held myHeld() {
        MobCard base = MobCards.byId(shown.my());
        return base == null ? null : new Held(base, num(BattleSyncPayload.MY_LEVEL));
    }

    public static Held oppHeld() {
        MobCard base = MobCards.byId(shown.opp());
        return base == null ? null : new Held(base, num(BattleSyncPayload.OPP_LEVEL));
    }

    public static Held mvpHeld() {
        MobCard base = MobCards.byId(text(BattleSyncPayload.T_MVP));
        return base == null ? null : new Held(base, num(BattleSyncPayload.MVP_LEVEL));
    }

    /** Your cards in play order, top card first, each at the level it plays at. */
    public static List<Held> hand() {
        int count = num(BattleSyncPayload.HAND_COUNT);
        List<Held> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MobCard card = MobCards.byId(text(BattleSyncPayload.TEXT_HEADER + i));
            if (card != null) {
                out.add(new Held(card, num(BattleSyncPayload.HEADER + i)));
            }
        }
        return out;
    }

    /** The recent rounds, oldest first, as {@link BattleSyncPayload#historyCode} values. */
    public static List<Integer> history() {
        int from = BattleSyncPayload.HEADER + num(BattleSyncPayload.HAND_COUNT);
        int count = num(BattleSyncPayload.HISTORY_COUNT);
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (from + i < shown.nums().size()) {
                out.add(shown.nums().get(from + i));
            }
        }
        return out;
    }

    // --- the end of a game ---------------------------------------------------

    public static int xp() { return num(BattleSyncPayload.XP); }
    public static int roundsWon() { return num(BattleSyncPayload.ROUNDS_WON); }
    public static int roundsLost() { return num(BattleSyncPayload.ROUNDS_LOST); }
    public static int roundsTied() { return num(BattleSyncPayload.ROUNDS_TIED); }
    public static int bestStreak() { return num(BattleSyncPayload.BEST_STREAK); }
    public static int mvpWins() { return num(BattleSyncPayload.MVP_WINS); }
    public static int emeralds() { return num(BattleSyncPayload.EMERALDS); }
    public static int rating() { return num(BattleSyncPayload.RATING); }
    public static int ratingDelta() { return num(BattleSyncPayload.RATING_DELTA); }
    public static String rank() { return text(BattleSyncPayload.T_RANK); }
    public static String note() { return text(BattleSyncPayload.T_NOTE); }
    public static String note2() { return text(BattleSyncPayload.T_NOTE2); }

}
