package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client snapshot of a table battle (vs CPU or a live duel), driving
 * the on-screen battle UI. {@code nums} packs the small integers in a fixed
 * order: [myCount, oppCount, potCount, round, chosenStat, chooser, winner,
 * difficulty, isPvp, myGames, oppGames]. A card id is empty ("") when that card
 * should render face-down / hidden. {@code label} is the opponent's name in a
 * duel (blank vs the CPU, which shows the difficulty instead).
 */
public record BattleSyncPayload(int phase, String playerCardId, String cpuCardId,
                                List<Integer> nums, String label)
        implements CustomPacketPayload {

    // phases
    public static final int PLAYER_PICK = 0;   // your turn — pick a stat
    public static final int CPU_PICK = 1;      // CPU's turn — you press reveal
    public static final int RESULT = 2;        // round resolved — flip & show
    public static final int FINISHED = 3;      // match over
    public static final int CLOSED = 4;        // close the screen
    public static final int OPPONENT_PICK = 5; // a human opponent's turn — you wait

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
                    BattleSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int num(int index, int fallback) {
        return index >= 0 && index < nums.size() ? nums.get(index) : fallback;
    }
}
