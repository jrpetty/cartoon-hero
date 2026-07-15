package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client snapshot of a solo (vs CPU) table battle, driving the
 * on-screen battle UI. {@code nums} packs the small integers in a fixed order:
 * [playerCount, cpuCount, potCount, round, chosenStat, chooser, winner, difficulty].
 * A card id is empty ("") when that card should render face-down / hidden.
 */
public record BattleSyncPayload(int phase, String playerCardId, String cpuCardId, List<Integer> nums)
        implements CustomPacketPayload {

    // phases
    public static final int PLAYER_PICK = 0;
    public static final int CPU_PICK = 1;
    public static final int RESULT = 2;
    public static final int FINISHED = 3;
    public static final int CLOSED = 4;

    public static final CustomPacketPayload.Type<BattleSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "battle_sync"));

    public static final StreamCodec<ByteBuf, BattleSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BattleSyncPayload::phase,
                    ByteBufCodecs.STRING_UTF8, BattleSyncPayload::playerCardId,
                    ByteBufCodecs.STRING_UTF8, BattleSyncPayload::cpuCardId,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BattleSyncPayload::nums,
                    BattleSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int num(int index, int fallback) {
        return index >= 0 && index < nums.size() ? nums.get(index) : fallback;
    }
}
