package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple 1-for-1 card trading through chat. The offerer holds the card they
 * want to give and runs /mobtrumps trade &lt;player&gt;; the target holds a card
 * and accepts to swap. Offered cards are escrowed and returned if cancelled.
 */
public final class TradeManager {

    private static final long TTL_MS = 60_000L;

    private record Offer(UUID offerer, ItemStack card, long expiresAt) {
    }

    /** target UUID -> pending offer. */
    private static final Map<UUID, Offer> PENDING = new ConcurrentHashMap<>();

    private TradeManager() {
    }

    public static int offer(ServerPlayer offerer, ServerPlayer target) {
        if (offerer.getUUID().equals(target.getUUID())) {
            offerer.sendSystemMessage(err("You can't trade with yourself."));
            return 0;
        }
        ItemStack held = offerer.getMainHandItem();
        if (MobCardItem.cardOf(held) == null) {
            offerer.sendSystemMessage(err("Hold the mob card you want to offer."));
            return 0;
        }
        if (PENDING.containsKey(target.getUUID())) {
            offerer.sendSystemMessage(err(name(target) + " already has a pending offer."));
            return 0;
        }
        ItemStack escrow = held.copyWithCount(1);
        held.shrink(1);
        PENDING.put(target.getUUID(), new Offer(offerer.getUUID(), escrow,
                System.currentTimeMillis() + TTL_MS));

        offerer.sendSystemMessage(Component.literal("Offered ").withStyle(ChatFormatting.GREEN)
                .append(escrow.getHoverName())
                .append(Component.literal(" to " + name(target) + ".").withStyle(ChatFormatting.GREEN)));
        target.sendSystemMessage(Component.literal(name(offerer) + " offers you ")
                .withStyle(ChatFormatting.GOLD)
                .append(escrow.getHoverName())
                .append(Component.literal(" for your held card. ").withStyle(ChatFormatting.GOLD))
                .append(BattleCommands.button("[Accept]", "/mobtrumps trade accept",
                        ChatFormatting.GREEN, "Swap your held card for theirs"))
                .append(Component.literal(" "))
                .append(BattleCommands.button("[Decline]", "/mobtrumps trade decline",
                        ChatFormatting.RED, "Decline the trade")));
        return 1;
    }

    public static int accept(ServerPlayer target) {
        Offer offer = PENDING.remove(target.getUUID());
        if (offer == null) {
            target.sendSystemMessage(err("You have no pending trade offer."));
            return 0;
        }
        ServerPlayer offerer = target.serverLevel().getServer().getPlayerList().getPlayer(offer.offerer());
        if (offer.expiresAt() < System.currentTimeMillis() || offerer == null) {
            target.sendSystemMessage(err("That trade offer has expired."));
            if (offerer != null) CardActions.give(offerer, offer.card());
            return 0;
        }
        ItemStack held = target.getMainHandItem();
        if (MobCardItem.cardOf(held) == null) {
            target.sendSystemMessage(err("Hold a mob card to give in return, then accept."));
            PENDING.put(target.getUUID(), offer);
            return 0;
        }
        ItemStack fromTarget = held.copyWithCount(1);
        held.shrink(1);

        CardActions.give(target, offer.card());
        CardActions.give(offerer, fromTarget);
        record(target, offer.card());
        record(offerer, fromTarget);

        Component done = Component.literal("Trade complete!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        offerer.sendSystemMessage(done);
        target.sendSystemMessage(done);
        for (ServerPlayer p : new ServerPlayer[]{offerer, target}) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        return 1;
    }

    public static int decline(ServerPlayer target) {
        Offer offer = PENDING.remove(target.getUUID());
        if (offer == null) {
            target.sendSystemMessage(err("You have no pending trade offer."));
            return 0;
        }
        ServerPlayer offerer = target.serverLevel().getServer().getPlayerList().getPlayer(offer.offerer());
        if (offerer != null) {
            CardActions.give(offerer, offer.card());
            offerer.sendSystemMessage(Component.literal(name(target) + " declined the trade.")
                    .withStyle(ChatFormatting.RED));
        }
        target.sendSystemMessage(Component.literal("Trade declined.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static void handleLogout(ServerPlayer player) {
        Offer incoming = PENDING.remove(player.getUUID());
        if (incoming != null) {
            ServerPlayer offerer = player.serverLevel().getServer()
                    .getPlayerList().getPlayer(incoming.offerer());
            if (offerer != null) CardActions.give(offerer, incoming.card());
        }
        PENDING.entrySet().removeIf(e -> {
            if (e.getValue().offerer().equals(player.getUUID())) {
                player.drop(e.getValue().card(), false);
                return true;
            }
            return false;
        });
    }

    private static void record(ServerPlayer player, ItemStack card) {
        var c = MobCardItem.cardOf(card);
        if (c != null) CollectionTracker.record(player, c.id(), MobCardItem.isFoilCard(card));
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
