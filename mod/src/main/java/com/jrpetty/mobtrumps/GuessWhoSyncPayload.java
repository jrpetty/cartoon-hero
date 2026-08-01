package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -&gt; client: the state of a Guess Who board.
 *
 * <p>{@code alive} is the authority on which faces are still standing — it is
 * computed on the server after each answer and simply drawn by the client, which
 * is what makes the elimination impossible to dodge.
 *
 * @param phase    see the PHASE_ constants on {@code GuessWhoManager}
 * @param asked    how many questions have been spent
 * @param alive    mob ids still possible
 * @param log      "templateIndex|value|1 or 0" per question asked
 * @param secret   the hidden mob — empty until the game is over
 * @param opponent "name|facesTheyHaveLeft|questionsTheyHaveAsked", empty when solo
 */
public record GuessWhoSyncPayload(int phase, int asked, List<String> alive,
                                  List<String> log, String secret, String opponent)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GuessWhoSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "guesswho_sync"));

    public static final StreamCodec<ByteBuf, GuessWhoSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GuessWhoSyncPayload::phase,
                    ByteBufCodecs.VAR_INT, GuessWhoSyncPayload::asked,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GuessWhoSyncPayload::alive,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GuessWhoSyncPayload::log,
                    ByteBufCodecs.STRING_UTF8, GuessWhoSyncPayload::secret,
                    ByteBufCodecs.STRING_UTF8, GuessWhoSyncPayload::opponent,
                    GuessWhoSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
