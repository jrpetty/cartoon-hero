package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server action in a table battle. {@code action} is one of the
 * constants below; {@code stat} is the chosen stat index for a PICK, the emote
 * for an EMOTE, and for a NEXT the {@link #ticket} of the state being left.
 */
public record BattleActionPayload(int action, int stat) implements CustomPacketPayload {

    public static final int PICK = 0;      // play stat on my card
    public static final int NEXT = 1;      // hurry the CPU's pick / advance past a result
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

    /**
     * What a NEXT is moving on from: the phase on screen and the round number.
     * The server acts on a NEXT only when this matches its own state, so a
     * second press that crosses on the wire with the game moving on — a
     * double tap, or the auto-continue firing just as you click — is dropped
     * instead of skipping a round the player never saw.
     */
    public static int ticket(int phase, int round) {
        return round * 8 + (phase & 7);
    }

    public static BattleActionPayload next(int phase, int round) {
        return new BattleActionPayload(NEXT, ticket(phase, round));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
