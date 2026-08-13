package com.voxelia.mmo.network;

import com.mojang.serialization.Codec;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientMemory;
import com.voxelia.mmo.game.MemoryGame;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: the Memory board as this viewer is allowed to see it. Hidden
 * cards carry no face, so the answer key never leaves the server.
 *
 * <p>{@code board} packs one int per card as {@code (state << 6) | (face + 1)};
 * hidden cards are simply 0. {@code meta} is a fixed-layout scalar block (see the
 * index constants) with the seats' scores appended, which keeps the payload to
 * three stream components.
 */
public record MemoryStatePayload(List<Integer> board, List<String> names, List<Integer> meta)
        implements CustomPacketPayload {

    public static final Type<MemoryStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "memory_state"));

    private static final StreamCodec<ByteBuf, List<Integer>> INTS =
        ByteBufCodecs.fromCodec(Codec.INT.listOf());
    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
        ByteBufCodecs.fromCodec(Codec.STRING.listOf());

    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryStatePayload> STREAM_CODEC =
        StreamCodec.composite(
            INTS, MemoryStatePayload::board,
            STRINGS, MemoryStatePayload::names,
            INTS, MemoryStatePayload::meta,
            MemoryStatePayload::new);

    // ── meta layout ──
    public static final int M_COLS = 0;
    public static final int M_ROWS = 1;
    public static final int M_TURN = 2;
    public static final int M_YOU = 3;
    public static final int M_MOVES = 4;
    public static final int M_PEEK_MS = 5;
    public static final int M_FINISHED = 6;
    public static final int M_ELAPSED = 7;
    public static final int M_PAIRS_LEFT = 8;
    public static final int M_OPENING = 9;
    public static final int M_WINNER = 10;   // seat + 1, so 0 means "no winner / draw"
    public static final int M_SCORES = 11;   // one entry per seat from here on

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** "You are not in a game" — the screen falls back to its lobby. */
    public static MemoryStatePayload empty() {
        return new MemoryStatePayload(List.of(), List.of(), List.of());
    }

    public static MemoryStatePayload of(MemoryGame game, UUID viewer, boolean opening) {
        List<Integer> board = new ArrayList<>(game.cards());
        for (int i = 0; i < game.cards(); i++) {
            int st = game.stateOf(i);
            board.add(st == MemoryGame.HIDDEN ? 0 : (st << 6) | (game.faceOf(i) + 1));
        }

        List<Integer> meta = new ArrayList<>();
        meta.add(game.difficulty().cols());
        meta.add(game.difficulty().rows());
        meta.add(game.turn());
        meta.add(game.indexOf(viewer));
        meta.add(game.moves());
        meta.add(game.peekMillis());
        meta.add(game.finished() ? 1 : 0);
        meta.add(game.elapsedSeconds());
        meta.add(game.pairsLeft());
        meta.add(opening ? 1 : 0);
        meta.add(game.winner() + 1);
        meta.addAll(game.scores());

        return new MemoryStatePayload(board, List.copyOf(game.names()), meta);
    }

    public static void handle(MemoryStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMemory.update(payload));
    }
}
