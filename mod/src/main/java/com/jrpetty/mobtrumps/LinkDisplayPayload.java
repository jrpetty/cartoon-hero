package com.jrpetty.mobtrumps;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client -> server: link a card from the sender's collection onto a card
 * display (empty {@code mobId} clears it). The server re-checks ownership of
 * both the display and the card, so nothing can be spoofed or stolen.
 */
public record LinkDisplayPayload(BlockPos pos, String mobId, boolean foil) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LinkDisplayPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "link_display"));

    public static final StreamCodec<ByteBuf, LinkDisplayPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LinkDisplayPayload::pos,
            ByteBufCodecs.STRING_UTF8, LinkDisplayPayload::mobId,
            ByteBufCodecs.BOOL, LinkDisplayPayload::foil,
            LinkDisplayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side handler with full validation. */
    public static void handle(LinkDisplayPayload payload, ServerPlayer player) {
        var level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > 64.0) {
            return; // too far away to be a legitimate click
        }
        if (!(level.getBlockEntity(pos) instanceof CardDisplayBlockEntity be)) {
            return;
        }
        if (!be.canEdit(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only " + be.getOwnerName()
                    + " can change this display.").withStyle(ChatFormatting.RED));
            return;
        }

        // empty id = clear
        if (payload.mobId().isEmpty()) {
            if (be.hasCard()) {
                be.clearProjection();
                player.sendSystemMessage(Component.literal("Display cleared.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        var card = com.jrpetty.mobtrumps.game.MobCards.byId(payload.mobId());
        if (card == null) {
            return;
        }
        boolean ownsBase = player.getData(ModAttachments.COLLECTED.get()).contains(card.id());
        boolean ownsFoil = player.getData(ModAttachments.COLLECTED_FOIL.get()).contains(card.id());
        if (!ownsBase) {
            player.sendSystemMessage(Component.literal("You haven't collected that card yet.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        boolean foil = payload.foil() && ownsFoil; // silently fall back to normal

        be.setProjection(card.id(), foil, player.getUUID(), player.getGameProfile().getName());
        player.sendSystemMessage(Component.literal("Now displaying " + (foil ? "your holographic " : "your ")
                        + card.displayName() + " card — it stays safely in your collection.")
                .withStyle(ChatFormatting.GREEN));
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.1F);
    }
}
