package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: something the player did at the Memory table.
 *
 * <p>{@code value} means whichever of "which tile" or "which board size" the
 * action needs. Nothing here is trusted: the server checks the turn, the peek,
 * the range and the tile's own state before it moves anything.
 */
public record MemoryActionPayload(int action, int value) implements CustomPacketPayload {

    public static final int START = 0;   // value = board size ordinal
    public static final int FLIP = 1;    // value = tile index
    public static final int QUIT = 2;
    public static final int SIZE = 3;    // value = board size ordinal
    public static final int CLOSE = 4;   // clear a finished board

    public static MemoryActionPayload flip(int tile) {
        return new MemoryActionPayload(FLIP, tile);
    }

    public static MemoryActionPayload start(int board) {
        return new MemoryActionPayload(START, board);
    }

    public static MemoryActionPayload size(int board) {
        return new MemoryActionPayload(SIZE, board);
    }

    public static MemoryActionPayload quit() {
        return new MemoryActionPayload(QUIT, 0);
    }

    public static MemoryActionPayload close() {
        return new MemoryActionPayload(CLOSE, 0);
    }

    public static final CustomPacketPayload.Type<MemoryActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "memory_action"));

    public static final StreamCodec<ByteBuf, MemoryActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MemoryActionPayload::action,
                    ByteBufCodecs.VAR_INT, MemoryActionPayload::value,
                    MemoryActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
