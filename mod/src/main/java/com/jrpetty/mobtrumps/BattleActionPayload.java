package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server action in a solo table battle. {@code action} is one of the
 * constants below; {@code stat} is the chosen stat index for a PICK.
 */
public record BattleActionPayload(int action, int stat) implements CustomPacketPayload {

    public static final int PICK = 0;      // play stat on my card
    public static final int NEXT = 1;      // reveal the CPU's pick / advance past a result
    public static final int FORFEIT = 2;   // leave the battle
    public static final int PLAY_AGAIN = 3; // rematch at the same difficulty (CPU)
    public static final int EMOTE = 4;     // send an emote (stat = emote index)
    public static final int REMATCH = 5;   // offer/accept a rematch after a duel (PvP)

    public static final CustomPacketPayload.Type<BattleActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "battle_action"));

    public static final StreamCodec<ByteBuf, BattleActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BattleActionPayload::action,
                    ByteBufCodecs.VAR_INT, BattleActionPayload::stat,
                    BattleActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
