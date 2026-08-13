package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the Memory board as this player is allowed to see it.
 *
 * <p>The faces list is the whole game, so it is built from
 * {@code Memory.Board.faceAt}, which returns {@code ""} for anything still face
 * down. A face-down tile therefore has no id ANYWHERE in this packet — not
 * blanked at the last moment, not encrypted, simply never put in. Someone
 * reading the traffic learns exactly what the player on the screen knows: which
 * tiles have been turned over, and what was on those.
 *
 * <p>Two lists rather than a dozen fields because StreamCodec.composite tops
 * out at six components, the same shape RankedSyncPayload and ProfileSyncPayload
 * use.
 */
public record MemorySyncPayload(List<String> texts, List<Integer> nums)
        implements CustomPacketPayload {

    // --- texts ---------------------------------------------------------------
    /** Whose board it is, and who they are playing; "" when solo. */
    public static final int T_YOU = 0;
    public static final int T_THEM = 1;
    public static final int TEXT_HEADER = 2;

    // --- numbers -------------------------------------------------------------
    public static final int PHASE = 0;
    public static final int RESULT = 1;
    public static final int BOARD = 2;      // Memory.BoardSize ordinal
    public static final int COLS = 3;
    public static final int ROWS = 4;
    public static final int TILES = 5;
    public static final int MOVES = 6;
    public static final int SCORE_YOU = 7;
    public static final int SCORE_THEM = 8;
    public static final int YOUR_TURN = 9;
    public static final int SOLO = 10;
    public static final int ELAPSED_S = 11;
    public static final int PEEK_MS = 12;   // 0 unless a miss is being shown
    public static final int HEADER = 13;

    /** No game: the client shows the board-size menu. */
    public static final int PHASE_MENU = 0;
    public static final int PHASE_PLAYING = 1;
    public static final int PHASE_OVER = 2;

    public static final int RESULT_NONE = 0;
    public static final int RESULT_WON = 1;
    public static final int RESULT_LOST = 2;
    public static final int RESULT_DRAW = 3;

    public static MemorySyncPayload menu(int board) {
        List<Integer> nums = new ArrayList<>();
        for (int i = 0; i < HEADER; i++) {
            nums.add(0);
        }
        nums.set(BOARD, board);
        return new MemorySyncPayload(List.of("", ""), nums);
    }

    public int num(int index) {
        return index >= 0 && index < nums.size() ? nums.get(index) : 0;
    }

    public String text(int index) {
        return index >= 0 && index < texts.size() ? texts.get(index) : "";
    }

    /** How many tiles actually travelled, whatever the header claims. */
    public int tileCount() {
        return Math.min(num(TILES), Math.min(Math.max(0, nums.size() - HEADER),
                Math.max(0, texts.size() - TEXT_HEADER)));
    }

    public int stateAt(int tile) {
        return num(HEADER + tile);
    }

    /** The mob on a tile, or "" if this client has not been told. */
    public String faceAt(int tile) {
        return text(TEXT_HEADER + tile);
    }

    public static final CustomPacketPayload.Type<MemorySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "memory_sync"));

    public static final StreamCodec<ByteBuf, MemorySyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MemorySyncPayload::texts,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MemorySyncPayload::nums,
                    MemorySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
