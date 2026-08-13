package com.voxelia.mmo.network;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.game.MemoryGame;
import com.voxelia.mmo.game.MemoryGames;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: one Memory action. {@code arg} is the card index for FLIP and
 * the {@link MemoryGame.Difficulty} ordinal for NEW_SOLO; unused otherwise.
 * Everything is re-validated server-side — the client can only ask.
 */
public record MemoryActionPacket(int action, int arg) implements CustomPacketPayload {

    public static final int FLIP = 0;
    public static final int NEW_SOLO = 1;
    public static final int LEAVE = 2;
    public static final int REFRESH = 3;

    public static final Type<MemoryActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "memory_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryActionPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MemoryActionPacket::action,
            ByteBufCodecs.VAR_INT, MemoryActionPacket::arg,
            MemoryActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MemoryActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (packet.action()) {
                case FLIP -> MemoryGames.flip(player, packet.arg());
                case NEW_SOLO -> {
                    MemoryGame.Difficulty[] all = MemoryGame.Difficulty.values();
                    int i = packet.arg();
                    if (i >= 0 && i < all.length) MemoryGames.startSolo(player, all[i]);
                }
                case LEAVE -> MemoryGames.leave(player, true);
                case REFRESH -> MemoryGames.syncTo(player);
                default -> { }
            }
        });
    }
}
