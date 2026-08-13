package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: everything the collection book's Profile page shows.
 *
 * <p>Carried as two lists rather than forty fields because
 * {@link StreamCodec#composite} tops out at six components. That is the same
 * shape {@link RankedSyncPayload} uses, and it costs the same thing: the
 * meaning of every slot lives in the index constants below rather than in the
 * type, so writer and reader must agree by hand. Read {@link #num} and
 * {@link #text} as the only way in — both fall back rather than throwing, so a
 * client one version behind a server renders a zero instead of crashing.
 *
 * <p>The trailing rivals are variable-length: {@link #RIVALS} of them, each
 * contributing one name to {@code texts} and {@code PER_RIVAL} numbers to
 * {@code nums}.
 */
public record ProfileSyncPayload(List<String> texts, List<Integer> nums)
        implements CustomPacketPayload {

    // --- texts ---------------------------------------------------------------
    public static final int T_NAME = 0;
    public static final int T_TITLE = 1;
    public static final int T_FAVOURITE = 2;   // "" when they have never picked
    public static final int T_NEMESIS = 3;     // "" when they have never lost
    public static final int TEXT_HEADER = 4;

    // --- numbers -------------------------------------------------------------
    public static final int RATING = 0;
    public static final int PEAK = 1;
    public static final int PLACE = 2;
    public static final int TOTAL_RANKED = 3;
    public static final int LB_WINS = 4;
    public static final int LB_LOSSES = 5;
    public static final int STREAK = 6;
    public static final int STREAK_BEST = 7;
    public static final int GIANT = 8;
    public static final int BADGES = 9;
    public static final int SEASON = 10;
    public static final int RANKED_WINS = 11;
    public static final int RANKED_LOSSES = 12;
    public static final int DUEL_WINS = 13;
    public static final int CPU_EASY = 14;
    public static final int CPU_NORMAL = 15;
    public static final int CPU_HARD = 16;
    public static final int CPU_TOTAL = 17;
    public static final int GAMES = 18;
    public static final int T21_PLAYED = 19;
    public static final int T21_WINS = 20;
    public static final int T21_EXACT = 21;
    public static final int GW_PLAYED = 22;
    public static final int GW_WINS = 23;
    public static final int GW_SWIFT = 24;
    public static final int GW_SHARP = 25;
    public static final int GW_HIGH = 26;
    public static final int BLUFF_WINS = 27;
    public static final int BLUFF_LOSSES = 28;
    public static final int BLUFF_CATCHES = 29;
    public static final int BLUFF_LASTCARD = 30;
    public static final int KILLS = 31;
    public static final int COLLECTED = 32;
    public static final int FOILS = 33;
    public static final int HOLO_MAX = 34;
    public static final int SETS_DONE = 35;
    public static final int SETS_TOTAL = 36;
    public static final int AWARDS_CLAIMED = 37;
    public static final int AWARDS_TOTAL = 38;
    public static final int RIVALS = 39;
    public static final int HEADER = 40;

    /** wins, losses — per rival, appended after the header. */
    public static final int PER_RIVAL = 2;

    public static ProfileSyncPayload empty() {
        List<Integer> zeros = new ArrayList<>();
        for (int i = 0; i < HEADER; i++) {
            zeros.add(0);
        }
        return new ProfileSyncPayload(List.of("", "", "", ""), zeros);
    }

    /** A header number, or {@code 0} if this client's payload is too short. */
    public int num(int index) {
        return index >= 0 && index < nums.size() ? nums.get(index) : 0;
    }

    /** A header string, or {@code ""} if this client's payload is too short. */
    public String text(int index) {
        return index >= 0 && index < texts.size() ? texts.get(index) : "";
    }

    /** How many rivals actually travelled, whatever the header claims. */
    public int rivalCount() {
        int byText = Math.max(0, texts.size() - TEXT_HEADER);
        int byNums = Math.max(0, (nums.size() - HEADER) / PER_RIVAL);
        return Math.min(num(RIVALS), Math.min(byText, byNums));
    }

    public String rivalName(int row) {
        return text(TEXT_HEADER + row);
    }

    public int rivalWins(int row) {
        return num(HEADER + row * PER_RIVAL);
    }

    public int rivalLosses(int row) {
        return num(HEADER + row * PER_RIVAL + 1);
    }

    public static final CustomPacketPayload.Type<ProfileSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "profile_sync"));

    public static final StreamCodec<ByteBuf, ProfileSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ProfileSyncPayload::texts,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ProfileSyncPayload::nums,
                    ProfileSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
