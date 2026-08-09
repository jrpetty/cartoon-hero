package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: the ranked standings.
 *
 * <p>Two fields rather than eight, because the composite stream codec tops out
 * at six and a table of players wants more than that. {@code names} is the
 * ladder in order; {@code nums} opens with a fixed header and then carries four
 * numbers per player:
 *
 * <pre>
 *   nums[0]  season number
 *   nums[1]  seconds left in the season
 *   nums[2]  index into names of the viewing player, or -1 if unranked
 *   nums[3]  the viewer's 1-based place on the full ladder, or -1
 *   nums[4]  how many players are ranked in total, which may exceed names.size()
 *   nums[5]  the rating decay will not push anyone below
 *   then, per player: rating, wins, losses, peak
 * </pre>
 *
 * <p>The viewer is always included even when they are nowhere near the top, so
 * a screen can show them their own row without a second request.
 */
public record RankedSyncPayload(List<String> names, List<Integer> nums)
        implements CustomPacketPayload {

    public static final int HEADER = 6;
    public static final int PER_PLAYER = 4;

    public static final int SEASON = 0;
    public static final int SECONDS_LEFT = 1;
    public static final int YOU_INDEX = 2;
    public static final int YOUR_PLACE = 3;
    public static final int TOTAL_RANKED = 4;
    public static final int DECAY_FLOOR = 5;

    public static final CustomPacketPayload.Type<RankedSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "ranked_sync"));

    public static final StreamCodec<ByteBuf, RankedSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RankedSyncPayload::names,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RankedSyncPayload::nums,
                    RankedSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int header(int index, int fallback) {
        return index >= 0 && index < nums.size() ? nums.get(index) : fallback;
    }

    /** How many players this snapshot actually carries rows for. */
    public int count() {
        return Math.min(names.size(), Math.max(0, (nums.size() - HEADER) / PER_PLAYER));
    }

    private int at(int row, int field, int fallback) {
        int i = HEADER + row * PER_PLAYER + field;
        return i >= 0 && i < nums.size() ? nums.get(i) : fallback;
    }

    public String name(int row) {
        return row >= 0 && row < names.size() ? names.get(row) : "";
    }

    public int rating(int row) {
        return at(row, 0, 0);
    }

    public int wins(int row) {
        return at(row, 1, 0);
    }

    public int losses(int row) {
        return at(row, 2, 0);
    }

    public int peak(int row) {
        return at(row, 3, 0);
    }
}
