package com.jrpetty.aztecabyss.item;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Heart of the Bridge: ten hearts, permanently, for holding the span.
 *
 * <h2>Why it is not an item</h2>
 *
 * <p>It was one, twice, and both versions were wrong. Bound to the off hand
 * it lost to a Totem of Undying forever - the arenas hand out three totems in
 * the grand prize alone, and a totem cancels a death where hearts only delay
 * one. Carried loose in the pack it was better, but it was still a thing that
 * could be dropped, burned, or left in a chest on the wrong day, and it could
 * be taken off you by the one event this reward is supposed to outlast:
 * dying.
 *
 * <p>So it is not a thing you hold. It is a thing that is true about you.
 * Clear the Bridge's last round and your maximum health is twenty points
 * higher from then on - through death, through respawn, through a server
 * restart, in every dimension, forever. There is nothing to lose and nothing
 * to carry.
 *
 * <p>Granted once. Holding the Bridge a second time is its own reward; it
 * does not stack into forty hearts.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID)
public final class HeartCore {

    private HeartCore() {
    }

    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(AztecAbyssConstants.MOD_ID, "bridge_heart");

    /** Twenty points: ten hearts, exactly doubling a player. */
    public static final double HEARTS = 20.0;

    /** Records the Bridge as held, and says so. Idempotent. */
    public static void grant(ServerPlayer player) {
        if (Boolean.TRUE.equals(player.getData(ModAttachments.BRIDGE_HEART.get()))) {
            player.displayClientMessage(Component.literal(
                    "§c✦ The Heart knows you already. §7Your ten hearts stand."), false);
            return;
        }
        player.setData(ModAttachments.BRIDGE_HEART.get(), Boolean.TRUE);
        apply(player);
        // Topped up as well as extended: finishing the Bridge should not leave
        // you standing there on half a bar with a bigger bar.
        player.setHealth(player.getMaxHealth());
        player.displayClientMessage(Component.literal(
                "§c§l✦ THE HEART IS YOURS").withStyle(ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.literal(
                "§7Ten hearts more, for good. §8Death does not take it back."), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /**
     * Keeps the modifier in step with the flag.
     *
     * <p>Re-checked on a slow tick rather than only at the moment it is
     * granted, because the attribute itself does not survive everything the
     * flag does: a respawned player is a fresh entity with fresh attributes,
     * and the flag is what carries across. One cheap check a second puts the
     * hearts back on before anybody notices they were gone.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (Boolean.TRUE.equals(player.getData(ModAttachments.BRIDGE_HEART.get()))) {
            apply(player);
        }
    }

    private static void apply(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.getModifier(HEALTH_ID) != null) {
            return;
        }
        health.addPermanentModifier(new AttributeModifier(
                HEALTH_ID, HEARTS, AttributeModifier.Operation.ADD_VALUE));
    }
}
