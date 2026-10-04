package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the state of a draft, from the receiving player's seat. The
 * first one opens the draft screen; {@link #CLOSED} shuts it.
 *
 * <p>{@code nums} is a fixed header addressed by the named indices below.
 * {@code texts} is the opponent's name and then three runs of card ids — the
 * pool still on offer, your picks, their picks — whose lengths are in the
 * header. Every card in a draft is face up to both players, so nothing here
 * is a secret from either of them.
 */
public record DraftSyncPayload(int phase, List<Integer> nums, List<String> texts)
        implements CustomPacketPayload {

    public static final int OPEN = 0;
    public static final int CLOSED = 1;

    // nums
    public static final int YOUR_TURN = 0;
    /** The number of the pick you are on, from 1. */
    public static final int PICK_NO = 1;
    public static final int PICKS_EACH = 2;
    /** Seconds left on the clock for the pick being made. */
    public static final int SECONDS = 3;
    public static final int POOL_COUNT = 4;
    public static final int MINE_COUNT = 5;
    public static final int THEIRS_COUNT = 6;
    public static final int HEADER = 7;

    // texts
    public static final int T_OPPONENT = 0;
    public static final int TEXT_HEADER = 1;

    public static final CustomPacketPayload.Type<DraftSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "draft_sync"));

    public static final StreamCodec<ByteBuf, DraftSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DraftSyncPayload::phase,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DraftSyncPayload::nums,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DraftSyncPayload::texts,
                    DraftSyncPayload::new);

    public static DraftSyncPayload open(String opponent, boolean yourTurn, int pickNo, int picksEach,
                                        int seconds, List<String> pool, List<String> mine,
                                        List<String> theirs) {
        List<Integer> nums = new ArrayList<>(List.of(yourTurn ? 1 : 0, pickNo, picksEach, seconds,
                pool.size(), mine.size(), theirs.size()));
        List<String> texts = new ArrayList<>(1 + pool.size() + mine.size() + theirs.size());
        texts.add(opponent == null ? "" : opponent);
        texts.addAll(pool);
        texts.addAll(mine);
        texts.addAll(theirs);
        return new DraftSyncPayload(OPEN, nums, texts);
    }

    public static DraftSyncPayload closed() {
        return new DraftSyncPayload(CLOSED, List.of(0, 0, 0, 0, 0, 0, 0), List.of(""));
    }

    public int num(int index) {
        return index >= 0 && index < nums.size() ? nums.get(index) : 0;
    }

    /** One of the three runs of ids: 0 the pool, 1 your picks, 2 theirs. */
    public List<String> run(int which) {
        int pool = num(POOL_COUNT);
        int mine = num(MINE_COUNT);
        int theirs = num(THEIRS_COUNT);
        int from = TEXT_HEADER + (which == 0 ? 0 : which == 1 ? pool : pool + mine);
        int count = which == 0 ? pool : which == 1 ? mine : theirs;
        int to = Math.min(texts.size(), from + count);
        return from >= to ? List.of() : List.copyOf(texts.subList(from, to));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
