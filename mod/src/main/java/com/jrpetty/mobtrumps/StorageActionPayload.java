package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: a binder storage action.
 *
 *   DEPOSIT_ALL - vacuum one of each loose card type from the inventory into the book
 *   WITHDRAW    - take one stored card ({@code mobId}, {@code foil}) back out as an item
 *   DEPOSIT_ONE - file one specific loose card from the inventory into the book
 */
public record StorageActionPayload(int action, String mobId, boolean foil) implements CustomPacketPayload {

    public static final int DEPOSIT_ALL = 0;
    public static final int WITHDRAW = 1;
    public static final int DEPOSIT_ONE = 2;

    public static final CustomPacketPayload.Type<StorageActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "storage_action"));

    public static final StreamCodec<ByteBuf, StorageActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageActionPayload::action,
            ByteBufCodecs.STRING_UTF8, StorageActionPayload::mobId,
            ByteBufCodecs.BOOL, StorageActionPayload::foil,
            StorageActionPayload::new);

    public static StorageActionPayload depositAll() {
        return new StorageActionPayload(DEPOSIT_ALL, "", false);
    }

    public static StorageActionPayload withdraw(String mobId, boolean foil) {
        return new StorageActionPayload(WITHDRAW, mobId, foil);
    }

    public static StorageActionPayload deposit(String mobId, boolean foil) {
        return new StorageActionPayload(DEPOSIT_ONE, mobId, foil);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
