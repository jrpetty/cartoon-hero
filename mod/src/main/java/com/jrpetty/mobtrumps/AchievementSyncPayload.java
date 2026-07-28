package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> client: the award board for the collection book's last pages.
 *
 * @param progress metric key -> current value (one entry per metric in use)
 * @param states   achievement id -> 1 unlocked, 2 claimed
 * @param eggPending  categories whose free spawn egg is waiting to be chosen
 * @param eggClaimed  "CATEGORY:mob_id" entries for eggs already taken
 */
public record AchievementSyncPayload(Map<String, Integer> progress, Map<String, Integer> states,
                                     List<String> eggPending, List<String> eggClaimed)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AchievementSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "achievement_sync"));

    public static final StreamCodec<ByteBuf, AchievementSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                            AchievementSyncPayload::progress,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                            AchievementSyncPayload::states,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                            AchievementSyncPayload::eggPending,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                            AchievementSyncPayload::eggClaimed,
                    AchievementSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
