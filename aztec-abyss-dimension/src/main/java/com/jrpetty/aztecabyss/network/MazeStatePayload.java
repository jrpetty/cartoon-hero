package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the maze HUD needs to draw, once a second.
 *
 * <p>The maze already told the player all of this - in a boss bar one line
 * long, in chat prints that scroll away, and in an action-bar fragment that
 * fights with every other action-bar user. The information existed; a place to
 * look at it did not. This packet is that place's feed.
 *
 * <p>One packet, whole state, no requests. The client never asks for a field,
 * because a client that asks is a client that draws stale answers between
 * round trips. It rides the same once-a-second cadence as the boss bar update
 * that has always run, from the same loop, so the server does no new walking
 * to produce it.
 *
 * <p>The clock is sent as raw phase plus the two lengths rather than as a
 * formatted string, so the HUD can tick smoothly *between* packets and the
 * countdown never visibly stutters. Everything else is a number the server
 * already had in hand.
 *
 * <p>Nineteen fields is past what {@code StreamCodec.composite} will take, so
 * the codec is written out by hand. Order in {@code write} and {@code read}
 * must match; there is a comment at each end saying so.
 */
public record MazeStatePayload(
        int day, int phase, int dayLen, int nightLen, boolean doorsOpen,
        int threatX10,
        String job, int jobLevel,
        int stings, int stingMax, int changingSeconds,
        int carrying, int runSeconds,
        int larder, int orderCommitted, int orderRemaining,
        int gladePct, int myPct,
        int escapeSeconds, boolean raid)
        implements CustomPacketPayload {

    public static final Type<MazeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "maze_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MazeStatePayload> STREAM_CODEC =
            StreamCodec.of(MazeStatePayload::write, MazeStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, MazeStatePayload p) {
        // Field order here is the wire format. read() below must mirror it.
        buf.writeVarInt(p.day);
        buf.writeVarInt(p.phase);
        buf.writeVarInt(p.dayLen);
        buf.writeVarInt(p.nightLen);
        buf.writeBoolean(p.doorsOpen);
        buf.writeVarInt(p.threatX10);
        buf.writeUtf(p.job);
        buf.writeVarInt(p.jobLevel);
        buf.writeVarInt(p.stings);
        buf.writeVarInt(p.stingMax);
        buf.writeVarInt(p.changingSeconds);
        buf.writeVarInt(p.carrying);
        buf.writeVarInt(p.runSeconds);
        buf.writeVarInt(p.larder);
        buf.writeVarInt(p.orderCommitted);
        buf.writeVarInt(p.orderRemaining);
        buf.writeVarInt(p.gladePct);
        buf.writeVarInt(p.myPct);
        buf.writeVarInt(p.escapeSeconds);
        buf.writeBoolean(p.raid);
    }

    private static MazeStatePayload read(RegistryFriendlyByteBuf buf) {
        // Mirrors write() above, field for field.
        return new MazeStatePayload(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readUtf(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
    }

    /** Seconds until the doors move next - to sealing by day, to opening by night. */
    public int secondsToDoorChange() {
        int end = phase >= dayLen ? dayLen + nightLen : dayLen;
        return Math.max(0, (end - phase) / 20);
    }

    public boolean isNight() {
        return phase >= dayLen;
    }

    @Override
    public Type<MazeStatePayload> type() {
        return TYPE;
    }
}
