package com.voxelia.mmo.network;

import com.mojang.serialization.Codec;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientPerks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server -> client: what each skill is actually giving you right now, one line per
 * skill in ordinal order ("+12.5% break speed, +25% Fortune on ores, Haste w/
 * pickaxe"). The server owns these numbers — they fold in config rates and the
 * player's talent ranks — so the client is told rather than guessing. {@code unlocks}
 * is {@link com.voxelia.mmo.progression.Milestones#unlockTable()}, so locked
 * abilities and passives can be drawn as locked.
 */
public record PerksSyncPayload(List<String> lines, List<Integer> unlocks) implements CustomPacketPayload {

    public static final Type<PerksSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "perks_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRINGS =
        ByteBufCodecs.fromCodec(Codec.STRING.listOf());
    private static final StreamCodec<ByteBuf, List<Integer>> INTS =
        ByteBufCodecs.fromCodec(Codec.INT.listOf());

    public static final StreamCodec<RegistryFriendlyByteBuf, PerksSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            STRINGS, PerksSyncPayload::lines,
            INTS, PerksSyncPayload::unlocks,
            PerksSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PerksSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPerks.update(payload.lines(), payload.unlocks()));
    }
}
