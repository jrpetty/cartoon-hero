package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client snapshot of a table battle (vs CPU, a campaign mission or a
 * live duel), driving the on-screen battle UI. Always told from the receiving
 * player's seat: "my" is the reader's side, "opp" the other one.
 *
 * <p>{@code nums} is a fixed header of small integers, addressed by the named
 * indices below, followed by two variable-length tails: the holo level of each
 * card in the reader's hand ({@link #HAND_COUNT} of them), then one code per
 * recent round ({@link #HISTORY_COUNT} of them, see {@link #historyCode}).
 * {@code texts} carries the strings, again as a fixed header and then the
 * reader's hand as card ids, top card first, matching the level tail.
 *
 * <p>A card id is empty ("") when that card should render face-down or not at
 * all. {@code label} is the opponent's name in a duel, or the mission's name.
 * Builders live in {@link BattleView}, so every game mode fills the same slots
 * the same way.
 */
public record BattleSyncPayload(int phase, String playerCardId, String cpuCardId,
                                List<Integer> nums, String label, List<String> texts)
        implements CustomPacketPayload {

    // phases
    public static final int PLAYER_PICK = 0;   // your turn — pick a stat
    public static final int CPU_PICK = 1;      // the CPU is choosing (it reveals on its own)
    public static final int RESULT = 2;        // round resolved — flip & show
    public static final int FINISHED = 3;      // match over
    public static final int CLOSED = 4;        // close the screen
    public static final int OPPONENT_PICK = 5; // a human opponent's turn — you wait
    public static final int GAME_OVER = 6;     // one game of a series is over; the next is coming

    // --- nums: the fixed header -------------------------------------------
    public static final int MY_COUNT = 0;
    public static final int OPP_COUNT = 1;
    public static final int POT = 2;
    public static final int ROUND = 3;
    /** The stat played this round, -1 before one is chosen. */
    public static final int STAT = 4;
    /** Who chose the stat: 0 you, 1 them, 2 nobody yet. */
    public static final int CHOOSER = 5;
    /** Who won the round (or, once a game ends, the game): 0 you, 1 them, 2 a draw. */
    public static final int WINNER = 6;
    public static final int DIFFICULTY = 7;
    /** 1 in a duel against another player. */
    public static final int PVP = 8;
    public static final int MY_GAMES = 9;
    public static final int OPP_GAMES = 10;
    /** Length of the turn clock in seconds, 0 when nobody is on the clock. */
    public static final int TURN_SECONDS = 11;
    /** The coin that settled a drawn round: 0 none, 1 you pick next, 2 they do. */
    public static final int COIN = 12;
    /** The campaign mission being played, 0 outside the campaign. */
    public static final int MISSION = 13;
    public static final int MY_LEVEL = 14;
    public static final int OPP_LEVEL = 15;
    /** Games in the match: 1 for a single game, 3 or 5 for a series. */
    public static final int BEST_OF = 16;
    /** Experience this game paid you; 0 until it ends. */
    public static final int XP = 17;
    public static final int ROUNDS_WON = 18;
    public static final int ROUNDS_LOST = 19;
    public static final int ROUNDS_TIED = 20;
    public static final int BEST_STREAK = 21;
    /** Rounds your best card won, with its id in {@link #T_MVP}. */
    public static final int MVP_WINS = 22;
    public static final int MVP_LEVEL = 23;
    /** Emeralds the match paid you. */
    public static final int EMERALDS = 24;
    /** Your ranked rating after the match, 0 when it was not rated. */
    public static final int RATING = 25;
    public static final int RATING_DELTA = 26;
    /** 1 when the game feeds wins and awards; a random-deal practice game does not. */
    public static final int COUNTS = 27;
    public static final int HAND_COUNT = 28;
    public static final int HISTORY_COUNT = 29;
    /** Rounds before the game is called on cards held; 0 for no limit. */
    public static final int ROUND_LIMIT = 30;
    /** 1 when the game ended at the limit on cards held. */
    public static final int ON_TIME = 31;
    public static final int HEADER = 32;

    /** At most this many recent rounds travel in the history tail. */
    public static final int HISTORY_CAP = 24;

    // --- texts ---------------------------------------------------------------
    /** Card id of the game's best card on your side, "" if none. */
    public static final int T_MVP = 0;
    /** One line for the end of a match: "Steve forfeited", "Mission cleared". */
    public static final int T_NOTE = 1;
    /** A second line: a reward, a promotion, a head-to-head record. */
    public static final int T_NOTE2 = 2;
    /** Rank label after a rated match ("Gold II"), "" otherwise. */
    public static final int T_RANK = 3;
    public static final int TEXT_HEADER = 4;

    public static final CustomPacketPayload.Type<BattleSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "battle_sync"));

    public static final StreamCodec<ByteBuf, BattleSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BattleSyncPayload::phase,
                    ByteBufCodecs.STRING_UTF8, BattleSyncPayload::playerCardId,
                    ByteBufCodecs.STRING_UTF8, BattleSyncPayload::cpuCardId,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BattleSyncPayload::nums,
                    ByteBufCodecs.STRING_UTF8, BattleSyncPayload::label,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BattleSyncPayload::texts,
                    BattleSyncPayload::new);

    /**
     * One round of history from the reader's seat: the stat in the low three
     * bits, then who won it (0 you, 1 them, 2 a draw), then who chose it.
     */
    public static int historyCode(int stat, int winner, int chooser) {
        return (stat & 7) | (winner & 3) << 3 | (chooser & 1) << 5;
    }

    public static int historyStat(int code) {
        return code & 7;
    }

    public static int historyWinner(int code) {
        return (code >> 3) & 3;
    }

    public static int historyChooser(int code) {
        return (code >> 5) & 1;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int num(int index, int fallback) {
        return index >= 0 && index < nums.size() ? nums.get(index) : fallback;
    }

    public String text(int index) {
        return index >= 0 && index < texts.size() ? texts.get(index) : "";
    }
}
